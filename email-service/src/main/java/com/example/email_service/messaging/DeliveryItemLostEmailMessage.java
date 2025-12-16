package com.example.email_service.messaging;

import java.time.LocalDateTime;

public record DeliveryItemLostEmailMessage(
        Integer orderId,
        String customerEmail,
        String carrier,
        String trackingCode,
        Integer warehouseId,
        Integer productId,
        Integer quantityLost,
        LocalDateTime reportedAt,
        String correlationId) {}

