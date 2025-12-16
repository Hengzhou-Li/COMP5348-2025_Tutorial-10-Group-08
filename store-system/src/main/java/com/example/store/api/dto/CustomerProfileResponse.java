package com.example.store.api.dto;

public record CustomerProfileResponse(
    Integer customerId,
    String fullName) {
}