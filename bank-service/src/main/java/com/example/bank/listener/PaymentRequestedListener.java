package com.example.bank.listener;

import com.example.bank.model.PaymentRequestedMessage;
import com.example.bank.model.PaymentResultMessage;
import com.example.bank.service.BankProcessingService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentRequestedListener {

    @Autowired
    private BankProcessingService service;

    @RabbitListener(queues = "payment-requested")
    public void handlePaymentRequested(PaymentRequestedMessage message) {
        //log the customer id and order id
        System.out.println("Customer ID: " + message.getCustomerId());
        System.out.println("Order ID: " + message.getOrderId());
        System.out.println("Received payment request: " + message);
        PaymentResultMessage result = service.processPayment(message);
        service.sendPaymentResult(result);
    }
}
