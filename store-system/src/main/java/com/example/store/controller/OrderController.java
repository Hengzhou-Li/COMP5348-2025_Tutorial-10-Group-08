package com.example.store.controller;

import com.example.store.api.dto.AddOrderItemRequest;
import com.example.store.api.dto.AddOrderItemResponse;
import com.example.store.api.dto.CancelOrderResponse;
import com.example.store.api.dto.CreateOrderRequest;
import com.example.store.api.dto.CreateOrderResponse;
import com.example.store.api.dto.RequestPaymentResponse;
import com.example.store.api.dto.ReserveStockResponse;
import com.example.store.api.dto.ReduceOrderItemRequest;
import com.example.store.api.dto.ReduceOrderItemResponse;
import com.example.store.service.OrderSaga;
import com.example.store.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderSaga orderSaga;

    public OrderController(OrderService orderService, OrderSaga orderSaga) {
        this.orderService = orderService;
        this.orderSaga = orderSaga;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        CreateOrderResponse response = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<AddOrderItemResponse> addProductToOrder(
            @PathVariable Integer orderId,
            @Valid @RequestBody AddOrderItemRequest request) {
        AddOrderItemResponse response = orderService.addOrderItem(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/items/reduce")
    public ResponseEntity<ReduceOrderItemResponse> reduceProductQuantity(
            @PathVariable Integer orderId,
            @Valid @RequestBody ReduceOrderItemRequest request) {
        ReduceOrderItemResponse response = orderService.reduceOrderItem(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/reserve")
    public ResponseEntity<ReserveStockResponse> reserveStock(@PathVariable Integer orderId) {
        ReserveStockResponse response = orderSaga.reserveStock(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/payment")
    public ResponseEntity<RequestPaymentResponse> requestPayment(@PathVariable Integer orderId) {
        RequestPaymentResponse response = orderSaga.requestPayment(orderId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/payment/bypass-success")
    public ResponseEntity<Void> bypassPaymentSuccess(@PathVariable Integer orderId) {
        orderSaga.bypassPaymentAndSendDelivery(orderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(@PathVariable Integer orderId) {
        CancelOrderResponse response = orderSaga.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
