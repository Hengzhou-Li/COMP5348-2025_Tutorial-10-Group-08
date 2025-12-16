package com.example.deliveryco_service.dto;

import java.time.LocalDateTime;

public record DeliveryInTransitMessage(
        Integer orderId,
        String carrier,
        String trackingCode,
        String eta,
        LocalDateTime updatedAt) {}

