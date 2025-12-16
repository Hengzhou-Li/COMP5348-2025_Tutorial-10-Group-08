package com.example.store.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    Integer customerId,
    List<CartItemResponse> items,
    BigDecimal cartTotal) {
}