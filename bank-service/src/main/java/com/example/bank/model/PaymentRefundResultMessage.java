package com.example.bank.model;

import lombok.Data;

@Data
public class PaymentRefundResultMessage {
    private Integer orderId;
    private Integer paymentId;
    private Integer refundId;
    private String status; // COMPLETED / FAILED
    private String bankRefundReference;
    private String failureReason;
    private String correlationId;
}
