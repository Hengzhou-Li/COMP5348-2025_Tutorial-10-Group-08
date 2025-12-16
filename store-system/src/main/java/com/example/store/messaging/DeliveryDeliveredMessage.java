package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryDeliveredMessage(
        Integer orderId, String carrier, String trackingCode, LocalDateTime deliveredAt) {}
