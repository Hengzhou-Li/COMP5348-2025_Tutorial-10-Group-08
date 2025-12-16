package com.example.store.messaging;

public record OrderPlacedMessage(Integer orderId, String status, String correlationId) {}
