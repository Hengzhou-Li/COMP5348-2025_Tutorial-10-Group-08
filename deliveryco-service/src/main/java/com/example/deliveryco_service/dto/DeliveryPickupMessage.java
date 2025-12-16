package com.example.deliveryco_service.dto;

import java.time.LocalDateTime;

public record DeliveryPickupMessage(
        Integer orderId, String carrier, String trackingCode, LocalDateTime pickedUpAt) {}

