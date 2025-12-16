package com.example.bank.controller;

import com.example.bank.dto.PaymentRequestDto;
import com.example.bank.dto.PaymentResponseDto;
import com.example.bank.dto.RefundRequestDto;
import com.example.bank.dto.RefundResponseDto;
import com.example.bank.service.BankProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bank")
public class BankController {
    private final BankProcessingService bankService;

    public BankController(BankProcessingService bankService) {
        this.bankService = bankService;
    }

    @PostMapping("/payment")
    public ResponseEntity<PaymentResponseDto> requestPayment(@Valid @RequestBody PaymentRequestDto request) {
        // 调用 service 发送消息到 RabbitMQ
        PaymentResponseDto response = bankService.sendPaymentRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/refund")
    public ResponseEntity<RefundResponseDto> requestRefund(@Valid @RequestBody RefundRequestDto request) {
        RefundResponseDto response = bankService.sendRefundRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
