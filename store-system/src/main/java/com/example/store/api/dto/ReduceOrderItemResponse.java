package com.example.store.api.dto;

import java.math.BigDecimal;

public record ReduceOrderItemResponse(
        Integer orderId,
        Integer productId,
        Integer quantity,
        BigDecimal orderTotal) {
}
