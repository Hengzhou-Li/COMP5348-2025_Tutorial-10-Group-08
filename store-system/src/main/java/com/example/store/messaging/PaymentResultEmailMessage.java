package com.example.store.messaging;

public record PaymentResultEmailMessage(
        Integer orderId,
        Integer paymentId,
        String customerEmail,
        String status,
        String bankTransactionReference,
        String failureReason,
        String correlationId) {}
