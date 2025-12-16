package com.example.store.messaging;

import com.example.store.model.OutboxEvent;
import com.example.store.repository.OrderRepo;
import com.example.store.repository.OutboxEventRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepo outboxEventRepo;
    private final OrderRepo orderRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String orderPlacedQueueName;
    private final String orderAllocatedQueueName;
    private final String paymentRequestedQueueName;
    private final String deliveryReadyQueueName;
    private final String notificationEmailQueueName;
    private final String paymentRefundQueueName;

    public OutboxRelay(
            OutboxEventRepo outboxEventRepo,
            OrderRepo orderRepo,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${store.queue.order-placed:order-placed}") String orderPlacedQueueName,
            @Value("${store.queue.order-allocated:order-allocated}") String orderAllocatedQueueName,
            @Value("${store.queue.payment-requested:payment-requested}") String paymentRequestedQueueName,
            @Value("${store.queue.delivery-ready:delivery-ready}") String deliveryReadyQueueName,
            @Value("${store.queue.notification-email:notification-email}") String notificationEmailQueueName,
            @Value("${store.queue.payment-refund:payment-refund}") String paymentRefundQueueName) {
        this.outboxEventRepo = outboxEventRepo;
        this.orderRepo = orderRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.orderPlacedQueueName = orderPlacedQueueName;
        this.orderAllocatedQueueName = orderAllocatedQueueName;
        this.paymentRequestedQueueName = paymentRequestedQueueName;
        this.deliveryReadyQueueName = deliveryReadyQueueName;
        this.notificationEmailQueueName = notificationEmailQueueName;
        this.paymentRefundQueueName = paymentRefundQueueName;
    }

    @Scheduled(fixedDelayString = "${store.outbox.publisher.delay-ms:1000}")
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepo.findByPublishAtIsNullOrPublishAtLessThanEqual(LocalDateTime.now());
        for (OutboxEvent event : events) {
            if (isOrderPlaced(event)) {
                handleOrderPlacedEvent(event);
            } else if (isOrderAllocated(event)) {
                handleOrderAllocatedEvent(event);
            } else if (isPaymentRequested(event)) {
                handlePaymentRequestedEvent(event);
            } else if (isOrderReadyForPickup(event)) {
                handleOrderReadyForPickupEvent(event);
            } else if (isPaymentResultNotification(event)) {
                handlePaymentResultNotificationEvent(event);
            } else if (isPaymentRefundRequested(event)) {
                handlePaymentRefundRequestedEvent(event);
            } else if (isRefundStatusNotification(event)) {
                handleRefundStatusNotificationEvent(event);
            } else if (isDeliveryPickupNotification(event)) {
                handleDeliveryPickupNotificationEvent(event);
            } else if (isDeliveryInTransitNotification(event)) {
                handleDeliveryInTransitNotificationEvent(event);
            } else if (isDeliveryLostNotification(event)) {
                handleDeliveryLostNotificationEvent(event);
            } else if (isDeliveryDeliveredNotification(event)) {
                handleDeliveryDeliveredNotificationEvent(event);
            }
        }
    }

    private boolean isOrderPlaced(OutboxEvent event) {
        return "ORDER".equalsIgnoreCase(event.getAggregateType())
                && "OrderPlaced".equalsIgnoreCase(event.getEventType());
    }

    private boolean isOrderAllocated(OutboxEvent event) {
        return "ORDER".equalsIgnoreCase(event.getAggregateType())
                && "OrderAllocated".equalsIgnoreCase(event.getEventType());
    }

    private boolean isPaymentRequested(OutboxEvent event) {
        return "PAYMENT".equalsIgnoreCase(event.getAggregateType())
                && "PaymentRequested".equalsIgnoreCase(event.getEventType());
    }

    private boolean isOrderReadyForPickup(OutboxEvent event) {
        return "ORDER".equalsIgnoreCase(event.getAggregateType())
                && "OrderReadyForPickup".equalsIgnoreCase(event.getEventType());
    }

    private boolean isPaymentResultNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "PaymentResultNotification".equalsIgnoreCase(event.getEventType());
    }

    private boolean isPaymentRefundRequested(OutboxEvent event) {
        return "PAYMENT".equalsIgnoreCase(event.getAggregateType())
                && "RefundRequested".equalsIgnoreCase(event.getEventType());
    }

    private boolean isRefundStatusNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "RefundStatusNotification".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryPickupNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryPickupNotification".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryInTransitNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryInTransitNotification".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryLostNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryLostNotification".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryDeliveredNotification(OutboxEvent event) {
        return "EMAIL".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryDeliveredNotification".equalsIgnoreCase(event.getEventType());
    }
    private void handleOrderPlacedEvent(OutboxEvent event) {
        try {
            OrderPlacedMessage message = objectMapper.readValue(event.getPayload(), OrderPlacedMessage.class);
            rabbitTemplate.convertAndSend(orderPlacedQueueName, message);
            outboxEventRepo.delete(event);
            log.info("Sent OrderPlaced message for order {} to queue {}", message.orderId(), orderPlacedQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse OrderPlaced payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish OrderPlaced event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleOrderAllocatedEvent(OutboxEvent event) {
        try {
            OrderAllocatedMessage message = objectMapper.readValue(event.getPayload(), OrderAllocatedMessage.class);
            rabbitTemplate.convertAndSend(orderAllocatedQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent OrderAllocated message for order {} to queue {}", message.orderId(), orderAllocatedQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse OrderAllocated payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish OrderAllocated event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handlePaymentRequestedEvent(OutboxEvent event) {
        try {
            String rawPayload = event.getPayload();
            PaymentRequestedMessage message = objectMapper.readValue(rawPayload, PaymentRequestedMessage.class);
            
            // BUG FIX: If customerId is missing, fetch it from the order database
            PaymentRequestedMessage messageToSend = message;
            if (message.customerId() == null) {
                if (message.orderId() == null) {
                    throw new IllegalStateException(
                            "PaymentRequested message is missing both customerId and orderId. Cannot send to bank service.");
                }
                
                // Fetch customerId from order
                Integer recoveredCustomerId = orderRepo.findByIdWithCustomer(message.orderId())
                        .map(order -> {
                            var customer = order.getCustomer();
                            if (customer != null) {
                                return customer.getId();
                            }
                            return null;
                        })
                        .orElse(null);
                
                if (recoveredCustomerId == null) {
                    throw new IllegalStateException(
                            "PaymentRequested message missing customerId and could not recover from order " + message.orderId());
                }
                
                // Reconstruct message with recovered customerId
                messageToSend = new PaymentRequestedMessage(
                        message.orderId(),
                        message.paymentId(),
                        recoveredCustomerId,  // Fixed customerId
                        message.amount(),
                        message.paymentStatus(),
                        message.correlationId(),
                        message.idempotencyKey());
            }
            
            String messageAsJson = objectMapper.writeValueAsString(messageToSend);
            
            // Verify customerId is in the final JSON
            if (!messageAsJson.contains("\"customerId\":" + messageToSend.customerId())) {
                throw new IllegalStateException("Final message JSON is missing customerId. Cannot send to bank service.");
            }
            
            rabbitTemplate.convertAndSend(paymentRequestedQueueName, messageToSend);
            outboxEventRepo.delete(event);
            
            log.info(
                    "Sent PaymentRequested message for order {} payment {} customer {} to queue {}",
                    messageToSend.orderId(),
                    messageToSend.paymentId(),
                    messageToSend.customerId(),
                    paymentRequestedQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse PaymentRequested payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish PaymentRequested event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleOrderReadyForPickupEvent(OutboxEvent event) {
        try {
            OrderReadyForPickupMessage message =
                    objectMapper.readValue(event.getPayload(), OrderReadyForPickupMessage.class);
            rabbitTemplate.convertAndSend(deliveryReadyQueueName, message);
            outboxEventRepo.delete(event);
            log.info("Sent OrderReadyForPickup message for order {} to queue {}", message.orderId(), deliveryReadyQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse OrderReadyForPickup payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish OrderReadyForPickup event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handlePaymentResultNotificationEvent(OutboxEvent event) {
        try {
            PaymentResultEmailMessage message =
                    objectMapper.readValue(event.getPayload(), PaymentResultEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent PaymentResultNotification message for order {} to queue {} with status {}",
                    message.orderId(),
                    notificationEmailQueueName,
                    message.status());
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse PaymentResultNotification payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish PaymentResultNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handlePaymentRefundRequestedEvent(OutboxEvent event) {
        try {
            PaymentRefundMessage message = objectMapper.readValue(event.getPayload(), PaymentRefundMessage.class);
            rabbitTemplate.convertAndSend(paymentRefundQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent RefundRequested message for order {} payment {} refund {} to queue {}",
                    message.orderId(),
                    message.paymentId(),
                    message.refundId(),
                    paymentRefundQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse RefundRequested payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish RefundRequested event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleRefundStatusNotificationEvent(OutboxEvent event) {
        try {
            RefundStatusEmailMessage message =
                    objectMapper.readValue(event.getPayload(), RefundStatusEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent RefundStatusNotification message for order {} refund {} to queue {}",
                    message.orderId(),
                    message.refundId(),
                    notificationEmailQueueName);
        } catch (JsonProcessingException ex) {
            log.error(
                    "Failed to parse RefundStatusNotification payload for event {}: {}",
                    event.getId(),
                    ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish RefundStatusNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleDeliveryPickupNotificationEvent(OutboxEvent event) {
        try {
            DeliveryPickupEmailMessage message =
                    objectMapper.readValue(event.getPayload(), DeliveryPickupEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent DeliveryPickupNotification message for order {} to queue {}",
                    message.orderId(),
                    notificationEmailQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryPickupNotification payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish DeliveryPickupNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleDeliveryInTransitNotificationEvent(OutboxEvent event) {
        try {
            DeliveryInTransitEmailMessage message =
                    objectMapper.readValue(event.getPayload(), DeliveryInTransitEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent DeliveryInTransitNotification message for order {} to queue {}",
                    message.orderId(),
                    notificationEmailQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryInTransitNotification payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish DeliveryInTransitNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleDeliveryLostNotificationEvent(OutboxEvent event) {
        try {
            DeliveryLostEmailMessage message =
                    objectMapper.readValue(event.getPayload(), DeliveryLostEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent DeliveryLostNotification message for order {} to queue {}",
                    message.orderId(),
                    notificationEmailQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryLostNotification payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish DeliveryLostNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }

    private void handleDeliveryDeliveredNotificationEvent(OutboxEvent event) {
        try {
            DeliveryDeliveredEmailMessage message =
                    objectMapper.readValue(event.getPayload(), DeliveryDeliveredEmailMessage.class);
            rabbitTemplate.convertAndSend(notificationEmailQueueName, message);
            outboxEventRepo.delete(event);
            log.info(
                    "Sent DeliveryDeliveredNotification message for order {} to queue {}",
                    message.orderId(),
                    notificationEmailQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryDeliveredNotification payload for event {}: {}", event.getId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to publish DeliveryDeliveredNotification event {}: {}", event.getId(), ex.getMessage());
            throw ex;
        }
    }
}
