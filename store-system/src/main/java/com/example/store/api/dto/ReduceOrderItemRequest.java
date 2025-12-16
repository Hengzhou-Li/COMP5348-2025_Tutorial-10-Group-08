package com.example.store.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReduceOrderItemRequest(
        @NotNull Integer productId,
        @NotNull @Positive Integer quantity) {
}
