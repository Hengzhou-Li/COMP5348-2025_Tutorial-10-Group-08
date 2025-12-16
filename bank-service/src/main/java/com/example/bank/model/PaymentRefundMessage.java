package com.example.bank.model;

import lombok.Data;

@Data
public class PaymentRefundMessage {
    private Long customerId;
    private Long orderId;
    private Long paymentId;
    private Long refundId;
    private Double amount;
    private String status;
    private String correlationId;
}
