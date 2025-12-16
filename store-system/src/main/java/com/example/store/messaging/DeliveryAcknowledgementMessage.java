package com.example.store.messaging;

import java.time.LocalDateTime;

public record DeliveryAcknowledgementMessage(
        Integer orderId, String status, String carrier, String trackingCode, LocalDateTime acknowledgedAt) {}
