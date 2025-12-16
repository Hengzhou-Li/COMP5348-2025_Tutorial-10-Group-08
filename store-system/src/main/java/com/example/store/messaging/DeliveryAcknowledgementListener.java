package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class DeliveryAcknowledgementListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryAcknowledgementListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public DeliveryAcknowledgementListener(
            OrderSaga orderSaga, @Value("${store.queue.delivery-ack:delivery-ack}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.delivery-ack:delivery-ack}")
    public void handleDeliveryAcknowledgement(DeliveryAcknowledgementMessage message) {
        log.info("Received delivery acknowledgement for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handleDeliveryAcknowledgement(message);
        } catch (ResponseStatusException ex) {
            log.warn(
                    "Unable to process delivery acknowledgement for order {}: {}",
                    message.orderId(),
                    ex.getReason());
        }
    }
}
