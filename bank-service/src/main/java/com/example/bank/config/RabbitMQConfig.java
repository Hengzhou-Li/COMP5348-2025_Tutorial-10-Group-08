package com.example.bank.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue paymentRequestedQueue() {
        return new Queue("payment-requested", true);
    }

    @Bean
    public Queue paymentResultQueue() {
        return new Queue("payment-result", true);
    }

    @Bean
    public Queue paymentRefundQueue() {
        return new Queue("payment-refund", true);
    }

    @Bean
    public Queue paymentRefundResultQueue() {
        return new Queue("payment-refund-result", true);
    }
}
