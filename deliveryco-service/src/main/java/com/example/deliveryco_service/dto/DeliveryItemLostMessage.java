package com.example.deliveryco_service.dto;

import java.time.LocalDateTime;

public record DeliveryItemLostMessage(
        Integer orderId,
        String carrier,
        String trackingCode,
        Integer warehouseId,
        Integer productId,
        Integer quantityLost,
        LocalDateTime reportedAt,
        String correlationId) {}

