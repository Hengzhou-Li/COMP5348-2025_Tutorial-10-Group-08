package com.example.store.api.dto;

public record CreateOrderResponse(Integer orderId, String status, String correlationId) {
}
