package com.example.store.messaging;

import com.example.store.service.OrderSaga;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultListener.class);

    private final OrderSaga orderSaga;
    private final String queueName;

    public PaymentResultListener(OrderSaga orderSaga, @Value("${store.queue.payment-result:payment-result}") String queueName) {
        this.orderSaga = orderSaga;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.payment-result:payment-result}")
    public void handlePaymentResult(PaymentResultMessage message) {
        log.info("Received payment result for order {} on queue {}", message.orderId(), queueName);
        try {
            orderSaga.handlePaymentResult(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process payment result for order {}: {}", message.orderId(), ex.getReason());
        } catch (Exception ex) {
            throw ex;
        }
    }
}
