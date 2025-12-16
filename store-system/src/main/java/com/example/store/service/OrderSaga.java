package com.example.store.service;

import com.example.store.api.dto.CancelOrderResponse;
import com.example.store.api.dto.RequestPaymentResponse;
import com.example.store.api.dto.ReserveStockResponse;
import com.example.store.messaging.DeliveryAcknowledgementMessage;
import com.example.store.messaging.DeliveryDeliveredEmailMessage;
import com.example.store.messaging.DeliveryDeliveredMessage;
import com.example.store.messaging.DeliveryInTransitEmailMessage;
import com.example.store.messaging.DeliveryInTransitMessage;
import com.example.store.messaging.DeliveryLostEmailMessage;
import com.example.store.messaging.DeliveryItemLostMessage;
import com.example.store.messaging.DeliveryPickupEmailMessage;
import com.example.store.messaging.DeliveryPickupMessage;
import com.example.store.messaging.OrderReadyForPickupMessage;
import com.example.store.messaging.PaymentResultEmailMessage;
import com.example.store.messaging.PaymentResultMessage;
import com.example.store.model.CustomerOrder;
import com.example.store.model.Delivery;
import com.example.store.model.Fulfillment;
import com.example.store.model.FulfillmentItem;
import com.example.store.model.OrderItem;
import com.example.store.model.OutboxEvent;
import com.example.store.model.Payment;
import com.example.store.model.Refund;
import com.example.store.model.Product;
import com.example.store.repository.DeliveryRepo;
import com.example.store.repository.OrderItemRepo;
import com.example.store.repository.OrderRepo;
import com.example.store.repository.OutboxEventRepo;
import com.example.store.repository.PaymentRepo;
import com.example.store.repository.ProductRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderSaga {

    private static final Logger log = LoggerFactory.getLogger(OrderSaga.class);

    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final AllocationService allocationService;
    private final PaymentService paymentService;
    private final PaymentRepo paymentRepo;
    private final DeliveryRepo deliveryRepo;
    private final OutboxEventRepo outboxEventRepo;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;
    private final ProductRepo productRepo;

    public OrderSaga(
            OrderRepo orderRepo,
            OrderItemRepo orderItemRepo,
            AllocationService allocationService,
            PaymentService paymentService,
            PaymentRepo paymentRepo,
            DeliveryRepo deliveryRepo,
            OutboxEventRepo outboxEventRepo,
            RefundService refundService,
            ObjectMapper objectMapper,
            ProductRepo productRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.allocationService = allocationService;
        this.paymentService = paymentService;
        this.paymentRepo = paymentRepo;
        this.deliveryRepo = deliveryRepo;
        this.outboxEventRepo = outboxEventRepo;
        this.refundService = refundService;
        this.objectMapper = objectMapper;
        this.productRepo = productRepo;
    }

    @Transactional
    public ReserveStockResponse reserveStock(Integer orderId) {
        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        // If order is already allocated or processed, return success (idempotency)
        if ("ALLOCATED".equalsIgnoreCase(order.getStatus()) ||
                "FULFILLED".equalsIgnoreCase(order.getStatus()) ||
                "PAYMENT_REQUESTED".equalsIgnoreCase(order.getStatus())) {
            log.info("Order {} already processed with status {}. Skipping allocation.", orderId, order.getStatus());
            return new ReserveStockResponse(orderId, "Order already processed");
        }

        if (!"NEW".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order not in NEW status for allocation.");
        }

        List<OrderItem> orderItems = orderItemRepo.findByOrder(order);
        AllocationPlan plan = allocationService.planAllocation(orderItems);
        allocationService.reserveStock(order, plan);

        LocalDateTime now = LocalDateTime.now();
        order.setStatus("ALLOCATED");
        order.setUpdatedAt(now);
        orderRepo.save(order);

        persistOrderAllocatedEvent(order, now);

        return new ReserveStockResponse(order.getId(), order.getStatus());
    }

    @Transactional
    public CancelOrderResponse cancelOrder(Integer orderId) {
        CustomerOrder order = orderRepo.findByIdWithCustomer(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already cancelled.");
        }

        if (deliveryRequestAlreadySent(order)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order already sent to delivery and cannot be cancelled.");
        }

        LocalDateTime now = LocalDateTime.now();

        allocationService.releaseStock(order);

        // Delete any pending delivery outbox events to prevent delivery
        Optional<OutboxEvent> deliveryEvent = outboxEventRepo
                .findFirstByAggregateTypeAndAggregateIdAndEventTypeOrderByCreatedAtDesc(
                        "ORDER", order.getId(), "OrderReadyForPickup");
        if (deliveryEvent.isPresent()) {
            OutboxEvent event = deliveryEvent.get();
            // Only delete if not yet published
            if (event.getPublishAt() == null || event.getPublishAt().isAfter(now)) {
                outboxEventRepo.delete(event);
                log.info("Deleted pending delivery outbox event for cancelled order {}", order.getId());
            }
        }

        order.setStatus("CANCELLED");
        order.setUpdatedAt(now);
        orderRepo.save(order);

        // Also delete any delivery records that might have been created
        Delivery delivery = order.getDelivery();
        if (delivery != null) {
            deliveryRepo.delete(delivery);
            order.setDelivery(null);
            orderRepo.save(order);
            log.info("Deleted delivery record for cancelled order {}", order.getId());
        }

        Refund refund = null;
        Payment payment = order.getPayment();
        if (payment != null && !"FAILED".equalsIgnoreCase(payment.getStatus())) {
            refund = refundService.requestRefund(order, payment);
        }

        Integer refundId = refund != null ? refund.getId() : null;
        String refundStatus = refund != null ? refund.getStatus() : null;

        return new CancelOrderResponse(order.getId(), order.getStatus(), refundId, refundStatus);
    }

    @Transactional
    public RequestPaymentResponse requestPayment(Integer orderId) {
        CustomerOrder order = orderRepo.findByIdWithCustomer(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if (!"ALLOCATED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order not in ALLOCATED status for payment.");
        }

        Payment payment = paymentService.requestPayment(order);

        order.setStatus("PAYMENT_PENDING");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(order);

        return new RequestPaymentResponse(
                order.getId(),
                payment.getId(),
                payment.getAmount(),
                payment.getStatus(),
                order.getStatus(),
                payment.getBankTransactionReference());
    }

    @Transactional
    public void handlePaymentResult(PaymentResultMessage message) {
        CustomerOrder order = orderRepo.findById(message.orderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        Payment payment = order.getPayment();
        if (payment == null || (message.paymentId() != null && !message.paymentId().equals(payment.getId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Matching payment not found for order.");
        }

        String status = message.status();
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment status missing in message.");
        }

        LocalDateTime now = LocalDateTime.now();
        if ("SUCCESS".equalsIgnoreCase(status)) {
            if ("CONFIRMED".equalsIgnoreCase(payment.getStatus())) {
                return;
            }

            paymentService.recordPaymentSuccess(payment, message.bankTransactionReference(), now);
            order.setStatus("PAID");
            order.setUpdatedAt(now);
            orderRepo.save(order);
            persistOrderReadyForPickupEvent(order, payment, now);
            persistPaymentResultEmailEvent(order, payment, message, now);
        } else if ("FAILED".equalsIgnoreCase(status)) {
            if ("FAILED".equalsIgnoreCase(payment.getStatus())) {
                return;
            }

            paymentService.recordPaymentFailure(payment, message.bankTransactionReference());
            allocationService.releaseStock(order);
            order.setStatus("PAYMENT_FAILED");
            order.setUpdatedAt(now);
            orderRepo.save(order);
            persistPaymentResultEmailEvent(order, payment, message, now);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment status: " + status);
        }
    }

    @Transactional
    public void bypassPaymentAndSendDelivery(Integer orderId) {
        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        String currentStatus = order.getStatus();
        if (!"ALLOCATED".equalsIgnoreCase(currentStatus)
                && !"PAYMENT_PENDING".equalsIgnoreCase(currentStatus)
                && !"PAID".equalsIgnoreCase(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order not in a state that can bypass payment processing.");
        }

        LocalDateTime now = LocalDateTime.now();

        Payment payment = order.getPayment();
        if (payment == null) {
            payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getOrderTotal());
            payment.setStatus("CONFIRMED");
            payment.setRequestedAt(now);
            payment.setConfirmedAt(now);
            payment.setBankTransactionReference("BYPASS-" + orderId);
        } else {
            payment.setStatus("CONFIRMED");
            if (payment.getRequestedAt() == null) {
                payment.setRequestedAt(now);
            }
            payment.setConfirmedAt(now);
            if (payment.getBankTransactionReference() == null || payment.getBankTransactionReference().isBlank()) {
                payment.setBankTransactionReference("BYPASS-" + orderId);
            }
        }

        payment = paymentRepo.save(payment);
        order.setPayment(payment);

        order.setStatus("PAID");
        order.setUpdatedAt(now);
        orderRepo.save(order);

        persistOrderReadyForPickupEvent(order, payment, now);
        PaymentResultMessage message = new PaymentResultMessage(orderId, payment.getId(), "SUCCESS",
                payment.getBankTransactionReference(), null);
        persistPaymentResultEmailEvent(order, payment, message, now);
    }

    @Transactional
    public void handleDeliveryAcknowledgement(DeliveryAcknowledgementMessage message) {
        Integer orderId = message.orderId();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery acknowledgement missing order id.");
        }

        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled orders cannot be acknowledged.");
        }

        if ("DELIVERY_CONFIRMED".equalsIgnoreCase(order.getStatus())) {
            return;
        }

        LocalDateTime acknowledgementTime = message.acknowledgedAt() != null ? message.acknowledgedAt()
                : LocalDateTime.now();

        Delivery delivery = order.getDelivery();
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setOrder(order);
            delivery.setCreatedAt(acknowledgementTime);
        } else {
            delivery.setOrder(order);
            if (delivery.getCreatedAt() == null) {
                delivery.setCreatedAt(acknowledgementTime);
            }
        }

        if (message.status() != null && !message.status().isBlank()) {
            delivery.setStatus(message.status());
        } else if (delivery.getStatus() == null || delivery.getStatus().isBlank()) {
            delivery.setStatus("ACKNOWLEDGED");
        }
        if (message.carrier() != null && !message.carrier().isBlank()) {
            delivery.setCarrier(message.carrier());
        }
        if (message.trackingCode() != null && !message.trackingCode().isBlank()) {
            delivery.setTrackingCode(message.trackingCode());
        }

        order.setDelivery(delivery);
        deliveryRepo.save(delivery);

        order.setStatus("DELIVERY_CONFIRMED");
        order.setUpdatedAt(acknowledgementTime);
        orderRepo.save(order);
    }

    @Transactional
    public void handleDeliveryPickup(DeliveryPickupMessage message) {
        Integer orderId = message.orderId();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery pickup missing order id.");
        }

        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled orders cannot be picked up.");
        }

        LocalDateTime pickupTime = message.pickedUpAt() != null ? message.pickedUpAt() : LocalDateTime.now();

        Delivery delivery = order.getDelivery();
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setOrder(order);
            delivery.setCreatedAt(pickupTime);
        } else {
            delivery.setOrder(order);
            if (delivery.getCreatedAt() == null) {
                delivery.setCreatedAt(pickupTime);
            }
        }

        if (message.carrier() != null && !message.carrier().isBlank()) {
            delivery.setCarrier(message.carrier());
        }
        if (message.trackingCode() != null && !message.trackingCode().isBlank()) {
            delivery.setTrackingCode(message.trackingCode());
        }
        delivery.setStatus("PICKED_UP");
        order.setDelivery(delivery);
        deliveryRepo.save(delivery);
        order.setStatus("OUT_FOR_DELIVERY");
        order.setUpdatedAt(pickupTime);
        orderRepo.save(order);
        persistDeliveryPickupEmailEvent(order, delivery, message, pickupTime);
    }

    @Transactional
    public void handleDeliveryInTransit(DeliveryInTransitMessage message) {
        Integer orderId = message.orderId();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery in-transit update missing order id.");
        }

        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled orders cannot be in transit.");
        }

        LocalDateTime updateTime = message.updatedAt() != null ? message.updatedAt() : LocalDateTime.now();

        Delivery delivery = order.getDelivery();
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setOrder(order);
            delivery.setCreatedAt(updateTime);
        } else {
            delivery.setOrder(order);
            if (delivery.getCreatedAt() == null) {
                delivery.setCreatedAt(updateTime);
            }
        }

        if (message.carrier() != null && !message.carrier().isBlank()) {
            delivery.setCarrier(message.carrier());
        }
        if (message.trackingCode() != null && !message.trackingCode().isBlank()) {
            delivery.setTrackingCode(message.trackingCode());
        }
        delivery.setStatus("IN_TRANSIT");
        order.setDelivery(delivery);
        deliveryRepo.save(delivery);

        order.setStatus("IN_TRANSIT");
        order.setUpdatedAt(updateTime);
        orderRepo.save(order);

        persistDeliveryInTransitEmailEvent(order, delivery, message, updateTime);
    }

    @Transactional
    public void handleDeliveryDelivered(DeliveryDeliveredMessage message) {
        Integer orderId = message.orderId();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery delivered update missing order id.");
        }

        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if ("CANCELLED".equalsIgnoreCase(order.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancelled orders cannot be delivered.");
        }

        if ("DELIVERED".equalsIgnoreCase(order.getStatus())) {
            return;
        }

        LocalDateTime deliveredAt = message.deliveredAt() != null ? message.deliveredAt() : LocalDateTime.now();

        Delivery delivery = order.getDelivery();
        if (delivery == null) {
            delivery = new Delivery();
            delivery.setOrder(order);
            delivery.setCreatedAt(deliveredAt);
        } else {
            delivery.setOrder(order);
            if (delivery.getCreatedAt() == null) {
                delivery.setCreatedAt(deliveredAt);
            }
        }

        if (message.carrier() != null && !message.carrier().isBlank()) {
            delivery.setCarrier(message.carrier());
        }
        if (message.trackingCode() != null && !message.trackingCode().isBlank()) {
            delivery.setTrackingCode(message.trackingCode());
        }

        delivery.setStatus("DELIVERED");
        order.setDelivery(delivery);
        deliveryRepo.save(delivery);

        order.setStatus("DELIVERED");
        order.setUpdatedAt(deliveredAt);
        orderRepo.save(order);

        persistDeliveryDeliveredEmailEvent(order, delivery, message, deliveredAt);
    }

    @Transactional
    public void handleDeliveryItemLost(DeliveryItemLostMessage message) {
        Integer orderId = message.orderId();
        if (orderId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery lost update missing order id.");
        }

        CustomerOrder order = orderRepo.findByIdWithCustomer(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        Payment payment = order.getPayment();
        if (payment == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Order does not have a payment to refund for delivery loss.");
        }

        Integer productId = message.productId();
        if (productId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery lost update missing product id.");
        }

        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found for delivery loss."));

        Integer lostQuantity = message.quantityLost();
        if (lostQuantity == null || lostQuantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lost quantity must be greater than zero.");
        }

        BigDecimal unitPrice = product.getUnitPrice();
        if (unitPrice == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Product is missing unit price for refund calculation.");
        }

        LocalDateTime reportedAt = message.reportedAt() != null ? message.reportedAt() : LocalDateTime.now();
        Delivery delivery = order.getDelivery();

        BigDecimal refundAmount = unitPrice.multiply(BigDecimal.valueOf(lostQuantity.longValue()));

        refundService.requestRefund(order, payment, refundAmount, message.correlationId());
        persistDeliveryLostEmailEvent(order, delivery, message, reportedAt);
    }

    private void persistOrderAllocatedEvent(CustomerOrder order, LocalDateTime timestamp) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(order.getId());
        event.setEventType("OrderAllocated");
        String correlationId = orderCorrelation(order);
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(String.format(
                "{\"orderId\":%d,\"status\":\"%s\",\"correlationId\":\"%s\"}",
                order.getId(),
                order.getStatus(),
                correlationId));
        outboxEventRepo.save(event);
    }

    private void persistOrderReadyForPickupEvent(CustomerOrder order, Payment payment, LocalDateTime timestamp) {
        OrderReadyForPickupMessage payload = new OrderReadyForPickupMessage(
                order.getId(),
                order.getStatus(),
                payment.getId(),
                payment.getStatus(),
                orderCorrelation(order),
                buildWarehouseAssignments(order));

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(order.getId());
        event.setEventType("OrderReadyForPickup");
        String correlationId = orderCorrelation(order);
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        // event.setPublishAt(timestamp); //FOR INSTANT TESTING
        // 1 second delay
        // event.setPublishAt(timestamp.plusSeconds(1));
        event.setPublishAt(timestamp.plusMinutes(3));
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private void persistPaymentResultEmailEvent(
            CustomerOrder order, Payment payment, PaymentResultMessage message, LocalDateTime timestamp) {
        String correlationId = orderCorrelation(order);
        PaymentResultEmailMessage payload = new PaymentResultEmailMessage(
                order.getId(),
                payment != null ? payment.getId() : null,
                customerEmail(order),
                payment != null ? payment.getStatus() : null,
                payment != null ? payment.getBankTransactionReference() : null,
                message.failureReason(),
                correlationId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("EMAIL");
        event.setAggregateId(order.getId());
        event.setEventType("PaymentResultNotification");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private void persistDeliveryPickupEmailEvent(
            CustomerOrder order, Delivery delivery, DeliveryPickupMessage message, LocalDateTime timestamp) {
        String correlationId = orderCorrelation(order);
        DeliveryPickupEmailMessage payload = new DeliveryPickupEmailMessage(
                order.getId(),
                customerEmail(order),
                delivery != null ? delivery.getCarrier() : null,
                delivery != null ? delivery.getTrackingCode() : null,
                message.pickedUpAt() != null ? message.pickedUpAt() : timestamp,
                order.getStatus(),
                correlationId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("EMAIL");
        event.setAggregateId(order.getId());
        event.setEventType("DeliveryPickupNotification");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private void persistDeliveryInTransitEmailEvent(
            CustomerOrder order, Delivery delivery, DeliveryInTransitMessage message, LocalDateTime timestamp) {
        String correlationId = orderCorrelation(order);
        DeliveryInTransitEmailMessage payload = new DeliveryInTransitEmailMessage(
                order.getId(),
                customerEmail(order),
                delivery != null ? delivery.getCarrier() : null,
                delivery != null ? delivery.getTrackingCode() : null,
                message.eta(),
                order.getStatus(),
                timestamp,
                correlationId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("EMAIL");
        event.setAggregateId(order.getId());
        event.setEventType("DeliveryInTransitNotification");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private void persistDeliveryDeliveredEmailEvent(
            CustomerOrder order, Delivery delivery, DeliveryDeliveredMessage message, LocalDateTime timestamp) {
        String correlationId = orderCorrelation(order);
        DeliveryDeliveredEmailMessage payload = new DeliveryDeliveredEmailMessage(
                order.getId(),
                customerEmail(order),
                delivery != null ? delivery.getCarrier() : null,
                delivery != null ? delivery.getTrackingCode() : null,
                message.deliveredAt() != null ? message.deliveredAt() : timestamp,
                order.getStatus(),
                correlationId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("EMAIL");
        event.setAggregateId(order.getId());
        event.setEventType("DeliveryDeliveredNotification");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private void persistDeliveryLostEmailEvent(
            CustomerOrder order, Delivery delivery, DeliveryItemLostMessage message, LocalDateTime timestamp) {
        String correlationId = (message.correlationId() != null && !message.correlationId().isBlank())
                ? message.correlationId()
                : orderCorrelation(order);
        String carrier = message.carrier();
        if ((carrier == null || carrier.isBlank()) && delivery != null) {
            carrier = delivery.getCarrier();
        }

        String trackingCode = message.trackingCode();
        if ((trackingCode == null || trackingCode.isBlank()) && delivery != null) {
            trackingCode = delivery.getTrackingCode();
        }

        DeliveryLostEmailMessage payload = new DeliveryLostEmailMessage(
                order.getId(),
                customerEmail(order),
                carrier,
                trackingCode,
                message.productId(),
                message.quantityLost(),
                timestamp,
                correlationId);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("EMAIL");
        event.setAggregateId(order.getId());
        event.setEventType("DeliveryLostNotification");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private List<OrderReadyForPickupMessage.WarehouseAssignment> buildWarehouseAssignments(CustomerOrder order) {
        List<OrderReadyForPickupMessage.WarehouseAssignment> assignments = new ArrayList<>();
        for (Fulfillment fulfillment : order.getFulfillments()) {
            Integer warehouseId = fulfillment.getWarehouse() != null ? fulfillment.getWarehouse().getId() : null;
            List<OrderReadyForPickupMessage.Item> items = new ArrayList<>();
            for (FulfillmentItem fulfillmentItem : fulfillment.getItems()) {
                Integer productId = fulfillmentItem.getProduct() != null ? fulfillmentItem.getProduct().getId() : null;
                Integer quantity = fulfillmentItem.getQuantityPicked();
                items.add(new OrderReadyForPickupMessage.Item(productId, quantity));
            }
            assignments.add(new OrderReadyForPickupMessage.WarehouseAssignment(warehouseId, items));
        }
        return assignments;
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialise outbox payload.",
                    ex);
        }
    }

    private String orderCorrelation(CustomerOrder order) {
        return "ORDER-" + order.getId();
    }

    private String customerEmail(CustomerOrder order) {
        if (order.getCustomer() == null || order.getCustomer().getEmail() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Order missing customer email for notification.");
        }
        return order.getCustomer().getEmail();
    }

    private boolean deliveryRequestAlreadySent(CustomerOrder order) {
        String status = order.getStatus() != null ? order.getStatus().toUpperCase() : "";
        if ("DELIVERY_CONFIRMED".equals(status)
                || "OUT_FOR_DELIVERY".equals(status)
                || "IN_TRANSIT".equals(status)
                || "DELIVERED".equals(status)) {
            return true;
        }

        return outboxEventRepo
                .findFirstByAggregateTypeAndAggregateIdAndEventTypeOrderByCreatedAtDesc(
                        "ORDER", order.getId(), "OrderReadyForPickup")
                .map(event -> {
                    LocalDateTime publishAt = event.getPublishAt();
                    return publishAt == null || !publishAt.isAfter(LocalDateTime.now());
                })
                .orElse(false);
    }
}
