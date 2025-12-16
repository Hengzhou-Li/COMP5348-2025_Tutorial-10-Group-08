package com.example.store.messaging;

import java.math.BigDecimal;

public record RefundStatusEmailMessage(
        Integer orderId,
        Integer refundId,
        String customerEmail,
        BigDecimal amount,
        String status,
        String failureReason,
        String correlationId) {}
