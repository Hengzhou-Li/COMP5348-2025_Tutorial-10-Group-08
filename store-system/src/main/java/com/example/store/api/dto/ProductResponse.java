package com.example.store.api.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Integer id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        Boolean active) {
}
