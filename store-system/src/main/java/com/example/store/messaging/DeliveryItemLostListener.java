package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeliveryItemLostListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryItemLostListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public DeliveryItemLostListener(
            OrderSaga orderSaga, @Value("${store.queue.delivery-lost:delivery-lost}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.delivery-lost:delivery-lost}")
    public void handleDeliveryItemLost(DeliveryItemLostMessage message) {
        log.info("Received delivery loss event for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handleDeliveryItemLost(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process delivery loss for order {}: {}", message.orderId(), ex.getReason());
        }
    }
}
