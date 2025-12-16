package com.example.store.messaging;

import java.math.BigDecimal;

public record PaymentRequestedMessage(
                Integer orderId,
                Integer paymentId,
                Integer customerId,
                BigDecimal amount,
                String paymentStatus,
                String correlationId,
                String idempotencyKey) {
}
