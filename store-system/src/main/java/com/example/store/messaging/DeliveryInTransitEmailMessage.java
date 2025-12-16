package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryInTransitEmailMessage(
        Integer orderId,
        String customerEmail,
        String carrier,
        String trackingCode,
        String eta,
        String status,
        LocalDateTime updatedAt,
        String correlationId) {}
