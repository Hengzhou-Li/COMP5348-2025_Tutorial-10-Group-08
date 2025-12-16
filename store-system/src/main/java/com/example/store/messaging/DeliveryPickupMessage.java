package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryPickupMessage(
        Integer orderId, String carrier, String trackingCode, LocalDateTime pickedUpAt) {}
