package com.example.store.messaging;

import com.example.store.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentRefundResultListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundResultListener.class);

    private final RefundService refundService;
    private final String queueName;

    public PaymentRefundResultListener(
            RefundService refundService,
            @Value("${store.queue.payment-refund-result:payment-refund-result}") String queueName) {
        this.refundService = refundService;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.payment-refund-result:payment-refund-result}")
    public void handleRefundResult(PaymentRefundResultMessage message) {
        log.info("Received refund result for refund {} on queue {}", message.refundId(), queueName);
        try {
            refundService.handleRefundResult(message);
        } catch (ResponseStatusException ex) {
            log.warn("Unable to process refund result {}: {}", message.refundId(), ex.getReason());
        }
    }
}
