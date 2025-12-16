package com.example.store.service;

import com.example.store.messaging.PaymentRefundMessage;
import com.example.store.messaging.PaymentRefundResultMessage;
import com.example.store.messaging.RefundStatusEmailMessage;
import com.example.store.model.CustomerOrder;
import com.example.store.model.OutboxEvent;
import com.example.store.model.Payment;
import com.example.store.model.Refund;
import com.example.store.repository.OutboxEventRepo;
import com.example.store.repository.RefundRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepo refundRepo;
    private final OutboxEventRepo outboxEventRepo;
    private final ObjectMapper objectMapper;

    public RefundService(RefundRepo refundRepo, OutboxEventRepo outboxEventRepo, ObjectMapper objectMapper) {
        this.refundRepo = refundRepo;
        this.outboxEventRepo = outboxEventRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund requestRefund(CustomerOrder order, Payment payment) {
        BigDecimal amount = payment != null ? payment.getAmount() : null;
        return requestRefund(order, payment, amount, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Refund requestRefund(
            CustomerOrder order, Payment payment, BigDecimal amount, String correlationIdOverride) {
        if (payment == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No payment to refund for order.");
        }

        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount is required.");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund amount must be positive.");
        }

        LocalDateTime now = LocalDateTime.now();

        Refund refund = new Refund();
        refund.setOrder(order);
        refund.setAmount(amount);
        refund.setStatus("PENDING");
        refund.setCreatedAt(now);
        refund = refundRepo.save(refund);
        order.getRefunds().add(refund);

        String correlationId = (correlationIdOverride != null && !correlationIdOverride.isBlank())
                ? correlationIdOverride
                : correlationId(order);

        PaymentRefundMessage paymentPayload = new PaymentRefundMessage(order.getId(), payment.getId(), refund.getId(),
                order.getCustomer().getId(), refund.getAmount(), "REQUESTED", correlationId);
        persistOutboxEvent("PAYMENT", payment.getId(), "RefundRequested", correlationId, now, paymentPayload);

        RefundStatusEmailMessage emailPayload = new RefundStatusEmailMessage(
                order.getId(), refund.getId(), customerEmail(order), refund.getAmount(), "REQUESTED", null,
                correlationId);
        persistOutboxEvent("EMAIL", order.getId(), "RefundStatusNotification", correlationId, now, emailPayload);

        return refund;
    }

    @Transactional
    public void handleRefundResult(PaymentRefundResultMessage message) {
        if (message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund result message is required.");
        }

        Integer refundId = message.refundId();
        if (refundId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund ID missing in result message.");
        }

        Refund refund = refundRepo.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund not found."));

        String status = message.status();
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Refund status missing in result message.");
        }

        if (!status.equalsIgnoreCase(refund.getStatus())) {
            refund.setStatus(status);
        }

        if (message.bankRefundReference() != null && !message.bankRefundReference().isBlank()) {
            refund.setBankRefundReference(message.bankRefundReference());
        }

        refundRepo.save(refund);

        CustomerOrder order = refund.getOrder();
        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Refund result cannot be processed without order context.");
        }

        String correlationId = message.correlationId() != null ? message.correlationId() : correlationId(order);
        LocalDateTime now = LocalDateTime.now();

        RefundStatusEmailMessage emailPayload = new RefundStatusEmailMessage(
                order.getId(),
                refund.getId(),
                customerEmail(order),
                refund.getAmount(),
                status,
                message.failureReason(),
                correlationId);

        persistOutboxEvent("EMAIL", order.getId(), "RefundStatusNotification", correlationId, now, emailPayload);
    }

    private void persistOutboxEvent(
            String aggregateType,
            Integer aggregateId,
            String eventType,
            String correlationId,
            LocalDateTime timestamp,
            Object payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(writePayload(payload));
        outboxEventRepo.save(event);
    }

    private String writePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to serialise outbox payload.",
                    ex);
        }
    }

    private String correlationId(CustomerOrder order) {
        return "ORDER-" + order.getId();
    }

    private String customerEmail(CustomerOrder order) {
        if (order.getCustomer() == null || order.getCustomer().getEmail() == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Order missing customer email for notification.");
        }
        return order.getCustomer().getEmail();
    }
}
