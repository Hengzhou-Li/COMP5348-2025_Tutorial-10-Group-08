package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class OrderAllocationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderAllocationListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public OrderAllocationListener(
            OrderSaga orderSaga, @Value("${store.queue.order-allocated:order-allocated}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.order-allocated:order-allocated}")
    public void handleOrderAllocated(OrderAllocatedMessage message) {
        Integer orderId = message.orderId();
        log.info("Received OrderAllocated message from queue {} for order {}", queueName, orderId);
        try {
            orderSaga.requestPayment(orderId);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to request payment for order {}: {}", orderId, ex.getReason());
        }
    }
}
