package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeliveryPickupListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryPickupListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public DeliveryPickupListener(
            OrderSaga orderSaga, @Value("${store.queue.delivery-picked:delivery-picked}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.delivery-picked:delivery-picked}")
    public void handleDeliveryPickup(DeliveryPickupMessage message) {
        log.info("Received delivery pickup for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handleDeliveryPickup(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process delivery pickup for order {}: {}", message.orderId(), ex.getReason());
        }
    }
}
