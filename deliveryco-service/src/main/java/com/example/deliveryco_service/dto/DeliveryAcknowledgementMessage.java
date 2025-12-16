package com.example.deliveryco_service.dto;

import java.time.LocalDateTime;

public record DeliveryAcknowledgementMessage(
        Integer orderId, String status, String carrier, String trackingCode, LocalDateTime acknowledgedAt) {}

