package com.example.deliveryco_service.dto;

import java.time.LocalDateTime;

public record DeliveryDeliveredMessage(
        Integer orderId, String carrier, String trackingCode, LocalDateTime deliveredAt) {}

