package com.example.store.service;

import com.example.store.model.CustomerOrder;
import com.example.store.model.OutboxEvent;
import com.example.store.model.Payment;
import com.example.store.repository.OutboxEventRepo;
import com.example.store.repository.PaymentRepo;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private final PaymentRepo paymentRepo;
    private final OutboxEventRepo outboxEventRepo;

    public PaymentService(PaymentRepo paymentRepo, OutboxEventRepo outboxEventRepo) {
        this.paymentRepo = paymentRepo;
        this.outboxEventRepo = outboxEventRepo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Payment requestPayment(CustomerOrder order) {
        if (order.getPayment() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment already requested for this order.");
        }

        // Ensure customer relationship is loaded and validated early in the transaction
        // This prevents lazy loading exceptions when building the payload
        Integer customerId = getCustomerId(order);

        LocalDateTime now = LocalDateTime.now();

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(order.getOrderTotal());
        payment.setStatus("PENDING");
        payment.setRequestedAt(now);
        payment = paymentRepo.save(payment);
        order.setPayment(payment);

        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("PAYMENT");
        event.setAggregateId(payment.getId());
        event.setEventType("PaymentRequested");
        event.setCorrelationId(buildCorrelationId(order));
        event.setCreatedAt(now);
        event.setPublishAt(now);
        
        String payload = buildPaymentRequestedPayload(order, payment, customerId);
        event.setPayload(payload);
        
        outboxEventRepo.save(event);

        return payment;
    }

    private String buildCorrelationId(CustomerOrder order) {
        return "ORDER-" + order.getId();
    }

    private String idempotencyKey(CustomerOrder order) {
        return String.valueOf(order.getId());
    }

    /**
     * Safely extracts customer ID from order, ensuring the customer relationship is loaded.
     * This prevents lazy loading exceptions by accessing the customer within the active transaction.
     * 
     * @param order The order entity
     * @return The customer ID
     * @throws ResponseStatusException if customer is null or not loaded
     */
    private Integer getCustomerId(CustomerOrder order) {
        if (order == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Order is null when extracting customer ID.");
        }
        
        try {
            // Access customer relationship early while still in transaction
            // This will trigger lazy loading if needed, or use already-loaded customer
            var customer = order.getCustomer();
            
            if (customer == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Order " + order.getId() + " has no associated customer. Customer relationship not loaded.");
            }
            
            Integer customerId = customer.getId();
            
            if (customerId == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Customer entity for order " + order.getId() + " has no ID.");
            }
            
            return customerId;
        } catch (org.hibernate.LazyInitializationException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Customer relationship not loaded for order " + order.getId() + ". "
                    + "Ensure order is loaded with JOIN FETCH o.customer. Error: " + e.getMessage(),
                    e);
        }
    }

    private String buildPaymentRequestedPayload(CustomerOrder order, Payment payment, Integer customerId) {
        // CRITICAL: Validate customerId is not null - this is required!
        if (customerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cannot build payment request payload: customerId is null for order " + order.getId());
        }
        
        String correlationId = buildCorrelationId(order);
        String idempotencyKey = idempotencyKey(order);
        
        // Build JSON manually to ensure customerId is always included
        // Using String.format with %d will fail if customerId is null, so we validate above
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        jsonBuilder.append("\"orderId\":").append(order.getId()).append(",");
        jsonBuilder.append("\"paymentId\":").append(payment.getId()).append(",");
        jsonBuilder.append("\"customerId\":").append(customerId).append(",");  // CRITICAL: customerId must be included!
        jsonBuilder.append("\"amount\":").append(payment.getAmount().toPlainString()).append(",");
        jsonBuilder.append("\"paymentStatus\":\"").append(payment.getStatus()).append("\",");
        jsonBuilder.append("\"correlationId\":\"").append(correlationId).append("\",");
        jsonBuilder.append("\"idempotencyKey\":\"").append(idempotencyKey).append("\"");
        jsonBuilder.append("}");
        
        String payload = jsonBuilder.toString();
        
        // Verify customerId is in the payload
        if (!payload.contains("\"customerId\":" + customerId)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Generated payment payload is missing customerId field for order " + order.getId());
        }
        
        return payload;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentSuccess(Payment payment, String bankTransactionReference, LocalDateTime confirmedAt) {
        payment.setStatus("CONFIRMED");
        if (bankTransactionReference != null && !bankTransactionReference.isBlank()) {
            payment.setBankTransactionReference(bankTransactionReference);
        }
        payment.setConfirmedAt(confirmedAt);
        paymentRepo.save(payment);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordPaymentFailure(Payment payment, String bankTransactionReference) {
        payment.setStatus("FAILED");
        if (bankTransactionReference != null && !bankTransactionReference.isBlank()) {
            payment.setBankTransactionReference(bankTransactionReference);
        }
        payment.setConfirmedAt(null);
        paymentRepo.save(payment);
    }
}
