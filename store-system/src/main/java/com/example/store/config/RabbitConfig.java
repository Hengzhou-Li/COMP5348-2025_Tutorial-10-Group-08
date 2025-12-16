package com.example.store.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

    @Value("${store.queue.order-placed:order-placed}")
    private String orderPlacedQueueName;

    @Value("${store.queue.order-allocated:order-allocated}")
    private String orderAllocatedQueueName;

    @Value("${store.queue.payment-requested:payment-requested}")
    private String paymentRequestedQueueName;

    @Value("${store.queue.payment-result:payment-result}")
    private String paymentResultQueueName;

    @Value("${store.queue.payment-refund-result:payment-refund-result}")
    private String paymentRefundResultQueueName;

    @Value("${store.queue.delivery-ready:delivery-ready}")
    private String deliveryReadyQueueName;

    @Value("${store.queue.notification-email:notification-email}")
    private String notificationEmailQueueName;

    @Value("${store.queue.payment-refund:payment-refund}")
    private String paymentRefundQueueName;

    @Value("${store.queue.delivery-ack:delivery-ack}")
    private String deliveryAckQueueName;

    @Value("${store.queue.delivery-picked:delivery-picked}")
    private String deliveryPickedQueueName;

    @Value("${store.queue.delivery-in-transit:delivery-in-transit}")
    private String deliveryInTransitQueueName;

    @Value("${store.queue.delivery-delivered:delivery-delivered}")
    private String deliveryDeliveredQueueName;

    @Value("${store.queue.delivery-lost:delivery-lost}")
    private String deliveryLostQueueName;

    @Bean
    public Queue orderPlacedQueue() {
        return new Queue(orderPlacedQueueName, true);
    }

    @Bean
    public Queue orderAllocatedQueue() {
        return new Queue(orderAllocatedQueueName, true);
    }

    @Bean
    public Queue paymentRequestedQueue() {
        return new Queue(paymentRequestedQueueName, true);
    }

    @Bean
    public Queue paymentResultQueue() {
        return new Queue(paymentResultQueueName, true);
    }

    @Bean
    public Queue paymentRefundResultQueue() {
        return new Queue(paymentRefundResultQueueName, true);
    }

    @Bean
    public Queue deliveryReadyQueue() {
        return new Queue(deliveryReadyQueueName, true);
    }

    @Bean
    public Queue notificationEmailQueue() {
        return new Queue(notificationEmailQueueName, true);
    }

    @Bean
    public Queue paymentRefundQueue() {
        return new Queue(paymentRefundQueueName, true);
    }

    @Bean
    public Queue deliveryAckQueue() {
        return new Queue(deliveryAckQueueName, true);
    }

    @Bean
    public Queue deliveryPickedQueue() {
        return new Queue(deliveryPickedQueueName, true);
    }

    @Bean
    public Queue deliveryInTransitQueue() {
        return new Queue(deliveryInTransitQueueName, true);
    }

    @Bean
    public Queue deliveryDeliveredQueue() {
        return new Queue(deliveryDeliveredQueueName, true);
    }

    @Bean
    public Queue deliveryLostQueue() {
        return new Queue(deliveryLostQueueName, true);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
