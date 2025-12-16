package com.example.store.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CustomerOrderResponse(
        Integer orderId,
        BigDecimal orderTotal,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CustomerOrderItemResponse> items) {
}
