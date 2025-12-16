package com.example.store.api.dto;

public record CancelOrderResponse(Integer orderId, String orderStatus, Integer refundId, String refundStatus) {}
