package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderWorkflowListener {

    private static final Logger log = LoggerFactory.getLogger(OrderWorkflowListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public OrderWorkflowListener(OrderSaga orderSaga, @Value("${store.queue.order-placed:order-placed}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.order-placed:order-placed}")
    public void handleOrderPlaced(OrderPlacedMessage message) {
        Integer orderId = message.orderId();
        log.info("Received OrderPlaced message from queue {} for order {}", queueName, orderId);
        orderSaga.reserveStock(orderId);
    }
}
