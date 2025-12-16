package com.example.bank.model;

import lombok.Data;

@Data
public class PaymentResultMessage {
    private Integer orderId;
    private Integer paymentId;
    private String status; // SUCCESS / FAILED
    private String bankTransactionReference;
    private String failureReason;
}
