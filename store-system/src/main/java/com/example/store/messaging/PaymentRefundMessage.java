package com.example.store.messaging;

import java.math.BigDecimal;

public record PaymentRefundMessage(
                Integer orderId,
                Integer paymentId,
                Integer refundId,
                Integer customerId,
                BigDecimal amount,
                String status,
                String correlationId) {
}
