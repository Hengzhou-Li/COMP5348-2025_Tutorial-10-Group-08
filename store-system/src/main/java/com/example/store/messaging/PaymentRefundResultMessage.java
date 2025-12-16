package com.example.store.messaging;

public record PaymentRefundResultMessage(
        Integer orderId,
        Integer paymentId,
        Integer refundId,
        String status,
        String bankRefundReference,
        String failureReason,
        String correlationId) {}
