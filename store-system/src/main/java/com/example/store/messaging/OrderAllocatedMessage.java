package com.example.store.messaging;

public record OrderAllocatedMessage(Integer orderId, String status, String correlationId) {}
