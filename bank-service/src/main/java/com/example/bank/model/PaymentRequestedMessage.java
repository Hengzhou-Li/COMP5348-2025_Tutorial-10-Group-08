package com.example.bank.model;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequestedMessage {
    private Integer customerId;
    private Integer orderId;
    private Integer paymentId;
    private BigDecimal amount;
    private String paymentStatus;
    private String correlationId;
    private String idempotencyKey;
}
