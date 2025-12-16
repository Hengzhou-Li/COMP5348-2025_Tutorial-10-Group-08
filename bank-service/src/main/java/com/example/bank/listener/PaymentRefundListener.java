package com.example.bank.listener;

import com.example.bank.model.PaymentRefundMessage;
import com.example.bank.model.PaymentRefundResultMessage;
import com.example.bank.service.BankProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentRefundListener {

    @Autowired
    private BankProcessingService service;

    @RabbitListener(queues = "payment-refund")
    public void handleRefundRequest(PaymentRefundMessage message) {
        System.out.println("Received refund request: " + message);
        PaymentRefundResultMessage result = service.processRefund(message);
        service.sendRefundResult(result);
    }
}
