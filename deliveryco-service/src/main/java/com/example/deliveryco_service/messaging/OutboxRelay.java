package com.example.deliveryco_service.messaging;

import com.example.deliveryco_service.model.OutboxEvent;
import com.example.deliveryco_service.repository.OutboxEventRepo;
import com.example.deliveryco_service.dto.DeliveryAcknowledgementMessage;
import com.example.deliveryco_service.dto.DeliveryPickupMessage;
import com.example.deliveryco_service.dto.DeliveryInTransitMessage;
import com.example.deliveryco_service.dto.DeliveryDeliveredMessage;
import com.example.deliveryco_service.dto.DeliveryItemLostMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepo outboxEventRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String deliveryAckQueueName;
    private final String deliveryPickedQueueName;
    private final String deliveryInTransitQueueName;
    private final String deliveryDeliveredQueueName;
    private final String deliveryLostQueueName;

    public OutboxRelay(
            OutboxEventRepo outboxEventRepo,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${store.queue.delivery-ack:delivery-ack}") String deliveryAckQueueName,
            @Value("${store.queue.delivery-picked:delivery-picked}") String deliveryPickedQueueName,
            @Value("${store.queue.delivery-in-transit:delivery-in-transit}") String deliveryInTransitQueueName,
            @Value("${store.queue.delivery-delivered:delivery-delivered}") String deliveryDeliveredQueueName,
            @Value("${store.queue.delivery-lost:delivery-lost}") String deliveryLostQueueName) {
        this.outboxEventRepo = outboxEventRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.deliveryAckQueueName = deliveryAckQueueName;
        this.deliveryPickedQueueName = deliveryPickedQueueName;
        this.deliveryInTransitQueueName = deliveryInTransitQueueName;
        this.deliveryDeliveredQueueName = deliveryDeliveredQueueName;
        this.deliveryLostQueueName = deliveryLostQueueName;
    }

    /**
     * Polls the outbox table every second and publishes ready events
     */
    @Scheduled(fixedDelayString = "${store.outbox.publisher.delay-ms:1000}")
    @Transactional
    public void publishOutboxEvents() {
        // Only fetch DELIVERY events to avoid processing ORDER events from store-system
        List<OutboxEvent> events = outboxEventRepo.findByAggregateTypeOrderByCreatedAtAsc("DELIVERY");
        
        if (!events.isEmpty()) {
            log.info("--------[publish]-------- OutboxRelay: Found {} DELIVERY events to process", events.size());
        }
        
        for (OutboxEvent event : events) {
            try {
                log.debug("Processing event {}: type={}, aggregate={}", event.getId(), event.getEventType(), event.getAggregateType());
                
                if (isDeliveryAcknowledged(event)) {
                    handleDeliveryAcknowledgedEvent(event);
                } else if (isDeliveryPicked(event)) {
                    handleDeliveryPickedEvent(event);
                } else if (isDeliveryInTransit(event)) {
                    handleDeliveryInTransitEvent(event);
                } else if (isDeliveryDelivered(event)) {
                    handleDeliveryDeliveredEvent(event);
                } else if (isDeliveryItemLost(event)) {
                    handleDeliveryItemLostEvent(event);
                } else {
                    log.warn("Unexpected event type: {} for aggregate: {} (event {})", 
                            event.getEventType(), event.getAggregateType(), event.getId());
                }
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage(), e);
            }
        }
    }

    private boolean isDeliveryAcknowledged(OutboxEvent event) {
        return "DELIVERY".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryAcknowledged".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryPicked(OutboxEvent event) {
        return "DELIVERY".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryPicked".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryInTransit(OutboxEvent event) {
        return "DELIVERY".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryInTransit".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryDelivered(OutboxEvent event) {
        return "DELIVERY".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryDelivered".equalsIgnoreCase(event.getEventType());
    }

    private boolean isDeliveryItemLost(OutboxEvent event) {
        return "DELIVERY".equalsIgnoreCase(event.getAggregateType())
                && "DeliveryItemLost".equalsIgnoreCase(event.getEventType());
    }

    private void handleDeliveryAcknowledgedEvent(OutboxEvent event) {
        try {
            DeliveryAcknowledgementMessage message = objectMapper.readValue(event.getPayload(), DeliveryAcknowledgementMessage.class);
            rabbitTemplate.convertAndSend(deliveryAckQueueName, message);
            outboxEventRepo.delete(event);
            log.info("--------[publish]-------- Published delivery acknowledgement for order {} to queue '{}'", message.orderId(), deliveryAckQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryAcknowledgement payload for event {}: {}", event.getId(), ex.getMessage());
            throw new RuntimeException("Failed to parse DeliveryAcknowledgement message", ex);
        }
    }

    private void handleDeliveryPickedEvent(OutboxEvent event) {
        try {
            DeliveryPickupMessage message = objectMapper.readValue(event.getPayload(), DeliveryPickupMessage.class);
            rabbitTemplate.convertAndSend(deliveryPickedQueueName, message);
            outboxEventRepo.delete(event);
            log.info("--------[publish]-------- Published delivery picked for order {} to queue '{}'", message.orderId(), deliveryPickedQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryPickup payload for event {}: {}", event.getId(), ex.getMessage());
            throw new RuntimeException("Failed to parse DeliveryPickup message", ex);
        }
    }

    private void handleDeliveryInTransitEvent(OutboxEvent event) {
        try {
            DeliveryInTransitMessage message = objectMapper.readValue(event.getPayload(), DeliveryInTransitMessage.class);
            rabbitTemplate.convertAndSend(deliveryInTransitQueueName, message);
            outboxEventRepo.delete(event);
            log.info("--------[publish]-------- Published delivery in transit for order {} to queue '{}'", message.orderId(), deliveryInTransitQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryInTransit payload for event {}: {}", event.getId(), ex.getMessage());
            throw new RuntimeException("Failed to parse DeliveryInTransit message", ex);
        }
    }

    private void handleDeliveryDeliveredEvent(OutboxEvent event) {
        try {
            DeliveryDeliveredMessage message = objectMapper.readValue(event.getPayload(), DeliveryDeliveredMessage.class);
            rabbitTemplate.convertAndSend(deliveryDeliveredQueueName, message);
            outboxEventRepo.delete(event);
            log.info("--------[publish]-------- Published delivery delivered for order {} to queue '{}'", message.orderId(), deliveryDeliveredQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryDelivered payload for event {}: {}", event.getId(), ex.getMessage());
            throw new RuntimeException("Failed to parse DeliveryDelivered message", ex);
        }
    }

    private void handleDeliveryItemLostEvent(OutboxEvent event) {
        try {
            DeliveryItemLostMessage message = objectMapper.readValue(event.getPayload(), DeliveryItemLostMessage.class);
            rabbitTemplate.convertAndSend(deliveryLostQueueName, message);
            outboxEventRepo.delete(event);
            log.info("--------[lost]-------- Published delivery item lost for order {} (productId={}, quantity={}) to queue '{}'", 
                    message.orderId(), message.productId(), message.quantityLost(), deliveryLostQueueName);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse DeliveryItemLost payload for event {}: {}", event.getId(), ex.getMessage());
            throw new RuntimeException("Failed to parse DeliveryItemLost message", ex);
        }
    }
}
