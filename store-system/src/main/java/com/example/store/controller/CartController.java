package com.example.store.controller;

import com.example.store.api.dto.AddCartItemRequest;
import com.example.store.api.dto.CartResponse;
import com.example.store.api.dto.CreateOrderResponse;
import com.example.store.api.dto.UpdateCartItemRequest;
import com.example.store.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers/{customerId}/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@PathVariable Integer customerId) {
        CartResponse response = cartService.getCart(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @PathVariable Integer customerId, @Valid @RequestBody AddCartItemRequest request) {
        CartResponse response = cartService.addItem(customerId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateItem(
            @PathVariable Integer customerId,
            @PathVariable Integer productId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse response = cartService.updateItem(customerId, productId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable Integer customerId, @PathVariable Integer productId) {
        CartResponse response = cartService.removeItem(customerId, productId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clearCart(@PathVariable Integer customerId) {
        CartResponse response = cartService.clearCart(customerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/checkout")
    public ResponseEntity<CreateOrderResponse> checkout(@PathVariable Integer customerId) {
        CreateOrderResponse response = cartService.checkout(customerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
