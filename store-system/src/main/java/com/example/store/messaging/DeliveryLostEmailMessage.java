package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryLostEmailMessage(
        Integer orderId,
        String customerEmail,
        String carrier,
        String trackingCode,
        Integer productId,
        Integer quantityLost,
        LocalDateTime reportedAt,
        String correlationId) {}
