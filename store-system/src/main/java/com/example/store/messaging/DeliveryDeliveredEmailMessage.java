package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryDeliveredEmailMessage(
        Integer orderId,
        String customerEmail,
        String carrier,
        String trackingCode,
        LocalDateTime deliveredAt,
        String status,
        String correlationId) {}
