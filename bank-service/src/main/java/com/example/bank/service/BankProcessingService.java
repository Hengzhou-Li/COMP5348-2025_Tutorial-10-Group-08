package com.example.bank.service;

import com.example.bank.dto.PaymentRequestDto;
import com.example.bank.dto.PaymentResponseDto;
import com.example.bank.dto.RefundRequestDto;
import com.example.bank.dto.RefundResponseDto;
import com.example.bank.model.PaymentRequestedMessage;
import com.example.bank.model.PaymentRefundMessage;
import com.example.bank.model.PaymentRefundResultMessage;
import com.example.bank.model.PaymentResultMessage;
import com.example.bank.entity.CustomerBalance;
import com.example.bank.repository.CustomerBalanceRepository;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class BankProcessingService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CustomerBalanceRepository balanceRepository;

    @Transactional
    public PaymentResultMessage processPayment(PaymentRequestedMessage msg) {
        PaymentResultMessage result = new PaymentResultMessage();
        result.setOrderId(msg.getOrderId());
        result.setPaymentId(msg.getPaymentId());

        // Optional<CustomerBalance> opt =
        // balanceRepository.findById(msg.getCustomerId().intValue());
        if (msg.getCustomerId() == null) {
            result.setStatus("FAILED");
            result.setFailureReason("Customer ID is missing");
            return result;
        }

        Optional<CustomerBalance> opt = balanceRepository.findById(msg.getCustomerId());
        if (opt.isEmpty()) {
            result.setStatus("FAILED");
            result.setFailureReason("User not found");
            return result;
        }

        CustomerBalance customer = opt.get();
        BigDecimal amount = msg.getAmount();
        if (amount == null) {
            result.setStatus("FAILED");
            result.setFailureReason("Payment amount is missing");
            return result;
        }

        if (customer.getBalance().compareTo(amount) < 0) {
            result.setStatus("FAILED");
            result.setFailureReason("Insufficient funds");
            return result;
        }

        customer.setBalance(customer.getBalance().subtract(amount));
        balanceRepository.save(customer);

        result.setStatus("SUCCESS");
        result.setBankTransactionReference("BANKREF-" + msg.getPaymentId());
        return result;
    }

    public PaymentRefundResultMessage processRefund(PaymentRefundMessage msg) {
        PaymentRefundResultMessage result = new PaymentRefundResultMessage();
        result.setOrderId(msg.getOrderId().intValue());
        result.setPaymentId(msg.getPaymentId().intValue());
        result.setRefundId(msg.getRefundId().intValue());
        result.setCorrelationId(msg.getCorrelationId());

        Optional<CustomerBalance> opt = balanceRepository.findById(msg.getCustomerId().intValue());
        if (opt.isEmpty()) {
            result.setStatus("FAILED");
            result.setFailureReason("User not found");
            return result;
        }

        CustomerBalance customer = opt.get();
        BigDecimal amount = BigDecimal.valueOf(msg.getAmount());

        customer.setBalance(customer.getBalance().add(amount));
        balanceRepository.save(customer);

        result.setStatus("COMPLETED");
        result.setBankRefundReference("BANKREF-REFUND-" + msg.getRefundId());
        return result;
    }

    public void sendPaymentResult(PaymentResultMessage result) {
        System.out.println("==========================================");
        System.out.println("[BankService] Sending PaymentResultMessage to queue: payment-result");
        System.out.println("[BankService] Message details:");
        System.out.println("  - orderId: " + result.getOrderId());
        System.out.println("  - paymentId: " + result.getPaymentId());
        System.out.println("  - status: " + result.getStatus());
        System.out.println("  - bankTransactionReference: " + result.getBankTransactionReference());
        System.out.println("  - failureReason: " + result.getFailureReason());
        System.out.println("[BankService] Using RabbitTemplate to send message...");

        try {
            rabbitTemplate.convertAndSend("payment-result", result);
            System.out.println("[BankService] ✓ Message sent successfully to queue: payment-result");
            System.out.println("[BankService] Full message object: " + result);
        } catch (Exception e) {
            System.out.println("[BankService] ✗✗✗ ERROR: Failed to send message ✗✗✗");
            System.out.println("[BankService] Exception: " + e.getMessage());
            System.out.println("[BankService] Exception type: " + e.getClass().getName());
            throw e;
        }
        System.out.println("==========================================");
    }

    public void sendRefundResult(PaymentRefundResultMessage result) {
        rabbitTemplate.convertAndSend("payment-refund-result", result);
        System.out.println("Sent refund result: " + result);
    }

    public PaymentResponseDto sendPaymentRequest(PaymentRequestDto dto) {
        PaymentRequestedMessage msg = new PaymentRequestedMessage();
        msg.setOrderId(dto.getOrderId());
        msg.setPaymentId(dto.getPaymentId());
        msg.setCustomerId(dto.getCustomerId());
        msg.setAmount(dto.getAmount());
        msg.setPaymentStatus(dto.getPaymentStatus());
        msg.setCorrelationId(dto.getCorrelationId());
        msg.setIdempotencyKey(dto.getIdempotencyKey());

        rabbitTemplate.convertAndSend("payment-requested", msg);

        PaymentResponseDto response = new PaymentResponseDto();
        response.setOrderId(dto.getOrderId());
        response.setPaymentId(dto.getPaymentId());
        response.setStatus("PENDING");
        response.setBankTransactionReference(null);
        return response;
    }

    public RefundResponseDto sendRefundRequest(RefundRequestDto dto) {
        PaymentRefundMessage msg = new PaymentRefundMessage();
        msg.setOrderId(dto.getOrderId().longValue());
        msg.setPaymentId(dto.getPaymentId().longValue());
        msg.setRefundId(dto.getRefundId().longValue());
        msg.setAmount(dto.getAmount().doubleValue());
        msg.setStatus(dto.getStatus());
        msg.setCorrelationId(dto.getCorrelationId());

        rabbitTemplate.convertAndSend("payment-refund", msg);

        RefundResponseDto response = new RefundResponseDto();
        response.setOrderId(dto.getOrderId());
        response.setPaymentId(dto.getPaymentId());
        response.setRefundId(dto.getRefundId());
        response.setStatus("REQUESTED");
        response.setBankRefundReference(null);
        return response;
    }
}
