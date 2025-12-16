package com.example.store.messaging;

import java.util.List;

public record OrderReadyForPickupMessage(
        Integer orderId,
        String orderStatus,
        Integer paymentId,
        String paymentStatus,
        String correlationId,
        List<WarehouseAssignment> warehouses) {

    public record WarehouseAssignment(Integer warehouseId, List<Item> items) {}

    public record Item(Integer productId, Integer quantity) {}
}
