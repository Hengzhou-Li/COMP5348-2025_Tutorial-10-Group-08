package com.example.store.api.dto;

import java.math.BigDecimal;

public record CustomerOrderItemResponse(
        Integer productId,
        String productSku,
        String productName,
        Integer quantity,
        BigDecimal unitPrice) {
}
