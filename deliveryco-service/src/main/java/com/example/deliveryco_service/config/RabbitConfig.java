package com.example.deliveryco_service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
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
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}


