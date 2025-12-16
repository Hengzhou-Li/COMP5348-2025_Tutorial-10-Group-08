package com.example.store.messaging;

public record PaymentResultMessage(
        Integer orderId, Integer paymentId, String status, String bankTransactionReference, String failureReason) {}
