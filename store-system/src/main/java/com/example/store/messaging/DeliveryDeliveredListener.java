package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeliveryDeliveredListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDeliveredListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public DeliveryDeliveredListener(
            OrderSaga orderSaga, @Value("${store.queue.delivery-delivered:delivery-delivered}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.delivery-delivered:delivery-delivered}")
    public void handleDeliveryDelivered(DeliveryDeliveredMessage message) {
        log.info("Received delivery delivered notification for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handleDeliveryDelivered(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process delivery delivered for order {}: {}", message.orderId(), ex.getReason());
        }
    }
}
