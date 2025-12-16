package com.example.store.api.dto;

import java.math.BigDecimal;

public record CartItemResponse(
    Integer productId,
    String sku,
    String name,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineTotal) {
}