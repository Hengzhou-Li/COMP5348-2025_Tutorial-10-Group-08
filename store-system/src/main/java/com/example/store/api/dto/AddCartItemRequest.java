package com.example.store.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddCartItemRequest(
        @NotNull Integer productId,
        @NotNull @Positive Integer quantity) {
}
