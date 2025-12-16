package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryPickupEmailMessage(
        Integer orderId,
        String customerEmail,
        String carrier,
        String trackingCode,
        LocalDateTime pickedUpAt,
        String status,
        String correlationId) {}
