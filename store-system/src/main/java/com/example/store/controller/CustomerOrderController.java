package com.example.store.controller;

import com.example.store.api.dto.CustomerOrderResponse;
import com.example.store.service.OrderService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/orders")
public class CustomerOrderController {

    private final OrderService orderService;

    public CustomerOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<CustomerOrderResponse>> getOrders(@PathVariable Integer customerId) {
        List<CustomerOrderResponse> orders = orderService.getOrdersForCustomer(customerId);
        return ResponseEntity.ok(orders);
    }
}
