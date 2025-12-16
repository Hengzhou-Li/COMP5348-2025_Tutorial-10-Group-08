package com.example.deliveryco_service.messaging;

import com.example.deliveryco_service.dto.DeliveryReadyMessage;
import com.example.deliveryco_service.service.DeliveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeliveryReadyListener {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReadyListener.class);
    
    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public DeliveryReadyListener(DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }
    

    @RabbitListener(queues = "delivery-ready")
    public void handleDeliveryReady(DeliveryReadyMessage message) {
        log.info("Received Order from DeliveryReady QUEUE message: orderId={}, status={}, correlationId={}, warehouses={}", 
                message.orderId(), message.orderStatus(), message.correlationId(), 
                message.warehouses() != null ? message.warehouses().size() : 0);
        
        try {
            deliveryService.simulateDelivery(message.orderId(), message.warehouses());
            // log.info("🚀 Started delivery simulation for order: {}", message.orderId());
        } catch (Exception e) {
            log.error("--------[simulation]-------- Failed to start delivery simulation: {}", e.getMessage(), e);
        }
    }
}