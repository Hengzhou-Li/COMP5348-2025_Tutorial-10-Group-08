package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeliveryInTransitListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryInTransitListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public DeliveryInTransitListener(
            OrderSaga orderSaga, @Value("${store.queue.delivery-in-transit:delivery-in-transit}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.delivery-in-transit:delivery-in-transit}")
    public void handleDeliveryInTransit(DeliveryInTransitMessage message) {
        log.info("Received delivery in-transit update for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handleDeliveryInTransit(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process delivery in-transit for order {}: {}", message.orderId(), ex.getReason());
        }
    }
}
