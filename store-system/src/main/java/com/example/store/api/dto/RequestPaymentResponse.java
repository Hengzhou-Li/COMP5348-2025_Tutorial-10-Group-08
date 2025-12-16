package com.example.store.api.dto;

import java.math.BigDecimal;

public record RequestPaymentResponse(
        Integer orderId,
        Integer paymentId,
        BigDecimal amount,
        String paymentStatus,
        String orderStatus,
        String bankTransactionReference) {}
