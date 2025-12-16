package com.example.deliveryco_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.deliveryco_service.dto.DeliveryAcknowledgementMessage;
import com.example.deliveryco_service.dto.DeliveryPickupMessage;
import com.example.deliveryco_service.dto.DeliveryInTransitMessage;
import com.example.deliveryco_service.dto.DeliveryDeliveredMessage;
import com.example.deliveryco_service.dto.DeliveryReadyMessage;
import com.example.deliveryco_service.dto.DeliveryItemLostMessage;
import com.example.deliveryco_service.repository.DeliveryRepo;
import com.example.deliveryco_service.repository.DeliveryEventRepo;
import com.example.deliveryco_service.repository.OutboxEventRepo;
import com.example.deliveryco_service.model.Delivery;
import com.example.deliveryco_service.model.DeliveryEvent;
import com.example.deliveryco_service.model.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final DeliveryRepo deliveryRepo;
    private final DeliveryEventRepo deliveryEventRepo;
    private final OutboxEventRepo outboxEventRepo;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public DeliveryService(DeliveryRepo deliveryRepo, DeliveryEventRepo deliveryEventRepo, 
                         OutboxEventRepo outboxEventRepo, ObjectMapper objectMapper) {
        this.deliveryRepo = deliveryRepo;
        this.deliveryEventRepo = deliveryEventRepo;
        this.outboxEventRepo = outboxEventRepo;
        this.objectMapper = objectMapper;
    }

    //Simulates the entire delivery 
     //Creates delivery, then progresses through: PICKED_UP (for all warehouses) -> IN_TRANSIT -> DELIVERED
     //Simulates warehouse pickups with 5 seconds per warehouse
     
    public void simulateDelivery(Integer orderId, List<DeliveryReadyMessage.WarehouseAssignment> warehouses) {
        // Run simulation asynchronously to not block the message listener
        CompletableFuture.runAsync(() -> {
            try {
                log.info("--------[simulation]-------- Starting delivery simulation for order {} ({} warehouse(s))", orderId, warehouses != null ? warehouses.size() : 0);
                
                // Step 1: Create the delivery
                Delivery delivery = createDeliveryInternal(orderId);
                Integer deliveryId = delivery.getId();
                
                // Step 2: Simulate warehouse pickups sequentially
                // Track which warehouses have been visited/picked up
                List<DeliveryReadyMessage.WarehouseAssignment> visitedWarehouses = new ArrayList<>();
                // Track lost items to prevent losing the same item multiple times
                // Key format: "warehouseId-productId"
                Set<String> lostItems = new HashSet<>();
                
                if (warehouses != null && !warehouses.isEmpty()) {
                    log.info("--------[simulation]-------- Starting warehouse pickup simulation for delivery {} ({} warehouse(s))", deliveryId, warehouses.size());
                    
                    for (int i = 0; i < warehouses.size(); i++) {
                        DeliveryReadyMessage.WarehouseAssignment warehouse = warehouses.get(i);
                        int itemCount = warehouse.items() != null ? warehouse.items().stream()
                            .mapToInt(item -> item.quantity() != null ? item.quantity() : 0)
                            .sum() : 0;
                        
                        log.info("--------[simulation]-------- Picking up from warehouse {} ({} items) - delivery {}", 
                            warehouse.warehouseId(), itemCount, deliveryId);
                        
                        // Wait 5 seconds for each warehouse (regardless of item count)
                        Thread.sleep(5000);
                        
                        log.info("--------[simulation]-------- Completed pickup from warehouse {} - delivery {}", 
                            warehouse.warehouseId(), deliveryId);
                        
                        // Add this warehouse to visited list
                        visitedWarehouses.add(warehouse);
                        
                        // Check for lost items after each warehouse pickup (only from visited warehouses)
                        checkForLostItems(orderId, deliveryId, delivery.getTrackingCode(), delivery.getCarrier(), visitedWarehouses, "ORDER-" + orderId, lostItems);
                    }
                    
                    // Mark as PICKED_UP after all warehouses are picked up
                    updateDeliveryStatus(deliveryId, "PICKED_UP");
                    // Check for lost items from all visited warehouses
                    checkForLostItems(orderId, deliveryId, delivery.getTrackingCode(), delivery.getCarrier(), visitedWarehouses, "ORDER-" + orderId, lostItems);
                    log.info("--------[simulation]-------- Delivery {} picked up from all warehouses", deliveryId);
                } else {
                    // If no warehouses, just wait 5 seconds and mark as picked up
                    log.info("--------[simulation]-------- No warehouses specified, waiting 5 seconds before pickup (delivery {})...", deliveryId);
                    Thread.sleep(5000);
                    updateDeliveryStatus(deliveryId, "PICKED_UP");
                    // No warehouses visited, so nothing to check for lost items
                    checkForLostItems(orderId, deliveryId, delivery.getTrackingCode(), delivery.getCarrier(), visitedWarehouses, "ORDER-" + orderId, lostItems);
                    log.info("--------[simulation]-------- Delivery {} picked up", deliveryId);
                }
                
                // Step 3: Wait 5 seconds then mark as IN_TRANSIT
                log.info("--------[simulation]-------- Waiting 5 seconds before transit (delivery {})...", deliveryId);
                Thread.sleep(5000);
                updateDeliveryStatus(deliveryId, "IN_TRANSIT");
                // Check for lost items from all visited warehouses
                checkForLostItems(orderId, deliveryId, delivery.getTrackingCode(), delivery.getCarrier(), visitedWarehouses, "ORDER-" + orderId, lostItems);
                log.info("--------[simulation]-------- Delivery {} in transit to customer", deliveryId);
                
                // Step 4: Wait 5 seconds then mark as DELIVERED
                log.info("--------[simulation]-------- Waiting 5 seconds before delivery (delivery {})...", deliveryId);
                Thread.sleep(5000);
                updateDeliveryStatus(deliveryId, "DELIVERED");
                // Check for lost items from all visited warehouses
                checkForLostItems(orderId, deliveryId, delivery.getTrackingCode(), delivery.getCarrier(), visitedWarehouses, "ORDER-" + orderId, lostItems);
                log.info("--------[simulation]-------- Delivery {} delivered to customer", deliveryId);
                
                log.info("--------[simulation]-------- Delivery simulation completed for order {}", orderId);
                
            } catch (Exception e) {
                log.error("--------[simulation]-------- Delivery simulation failed for order {}: {}", orderId, e.getMessage(), e);
            }
        });
    }

    @Transactional
    public void createDelivery(Integer orderId) {
        createDeliveryInternal(orderId);
    }

    @Transactional
    private Delivery createDeliveryInternal(Integer orderId) {
        // This function is called when deliveryCO reads the message from the delivery-ready queue
        LocalDateTime now = LocalDateTime.now();
        
        // Carrier and tracking code is just a placeholder for now (TODO: get carrier from order)
        Delivery delivery = new Delivery(orderId, "DeliveryCO", "TRACK-" + orderId);
        delivery = deliveryRepo.save(delivery);

        // Create delivery event
        DeliveryEvent deliveryEvent = new DeliveryEvent(delivery.getId(), "READ_FOR_PICKUP", "Delivery created");
        deliveryEventRepo.save(deliveryEvent);

        // Create outbox event for delivery acknowledgement
        try {
            DeliveryAcknowledgementMessage message = new DeliveryAcknowledgementMessage(
                orderId,
                "ACKNOWLEDGED",
                delivery.getCarrier(),
                delivery.getTrackingCode(),
                now
            );
            String payload = objectMapper.writeValueAsString(message);
            OutboxEvent outboxEvent = new OutboxEvent(
                "DELIVERY",
                delivery.getId(),
                "DeliveryAcknowledged",
                payload,
                "ORDER-" + orderId
            );
            outboxEventRepo.save(outboxEvent);
            log.info("--------[publish]-------- Created outbox event for delivery acknowledgement {} (order {})", delivery.getId(), orderId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create outbox event for delivery acknowledgement", e);
        }
        
        return delivery;
    }

    @Transactional
    public void updateDeliveryStatus(Integer deliveryId, String status) {
        Delivery delivery = deliveryRepo.findById(deliveryId)
            .orElseThrow(() -> new RuntimeException("Delivery not found"));
        delivery.setStatus(status);
        
        LocalDateTime now = LocalDateTime.now();
        
        // Create delivery event
        DeliveryEvent deliveryEvent = new DeliveryEvent(deliveryId, status, "Delivery status updated to " + status);
        deliveryEventRepo.save(deliveryEvent);

        // Create outbox event based on the status
        try {
            OutboxEvent outboxEvent = null;
            
            if ("PICKED_UP".equals(status)) {
                DeliveryPickupMessage message = new DeliveryPickupMessage(
                    delivery.getOrderId(),
                    delivery.getCarrier(),
                    delivery.getTrackingCode(),
                    now
                );
                String payload = objectMapper.writeValueAsString(message);
                outboxEvent = new OutboxEvent(
                    "DELIVERY",
                    delivery.getId(),
                    "DeliveryPicked",
                    payload,
                    "ORDER-" + delivery.getOrderId()
                );
                log.info("--------[publish]-------- Created outbox event for delivery pickup {} (order {})", delivery.getId(), delivery.getOrderId());
                
            } else if ("IN_TRANSIT".equals(status)) {
                DeliveryInTransitMessage message = new DeliveryInTransitMessage(
                    delivery.getOrderId(),
                    delivery.getCarrier(),
                    delivery.getTrackingCode(),
                    "Tomorrow", // Simple ETA
                    now
                );
                String payload = objectMapper.writeValueAsString(message);
                outboxEvent = new OutboxEvent(
                    "DELIVERY",
                    delivery.getId(),
                    "DeliveryInTransit",
                    payload,
                    "ORDER-" + delivery.getOrderId()
                );
                log.info("--------[publish]-------- Created outbox event for delivery in transit {} (order {})", delivery.getId(), delivery.getOrderId());
                
            } else if ("DELIVERED".equals(status)) {
                DeliveryDeliveredMessage message = new DeliveryDeliveredMessage(
                    delivery.getOrderId(),
                    delivery.getCarrier(),
                    delivery.getTrackingCode(),
                    now
                );
                String payload = objectMapper.writeValueAsString(message);
                outboxEvent = new OutboxEvent(
                    "DELIVERY",
                    delivery.getId(),
                    "DeliveryDelivered",
                    payload,
                    "ORDER-" + delivery.getOrderId()
                );
                log.info("--------[publish]-------- Created outbox event for delivery delivered {} (order {})", delivery.getId(), delivery.getOrderId());
            }
            
            if (outboxEvent != null) {
                outboxEventRepo.save(outboxEvent);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create outbox event for delivery update", e);
        }
        
        deliveryRepo.save(delivery);
    }

    public String getDeliveryStatus(Integer deliveryId) {
        Delivery delivery = deliveryRepo.findById(deliveryId)
            .orElseThrow(() -> new RuntimeException("Delivery not found"));
        return delivery.getStatus();
    }

    
    // Checks each item in the order for loss (5% chance per item)
    // If an item is lost, creates an outbox event for the lost item for the store system to read
    // Uses lostItems set to track already-lost items and prevent losing the same item multiple times
    @Transactional
    public void checkForLostItems(Integer orderId, Integer deliveryId, String trackingCode, 
                                  String carrier, List<DeliveryReadyMessage.WarehouseAssignment> warehouses, 
                                  String correlationId, Set<String> lostItems) {
        if (warehouses == null || warehouses.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (DeliveryReadyMessage.WarehouseAssignment warehouse : warehouses) {
            if (warehouse.items() == null || warehouse.items().isEmpty()) {
                continue;
            }

            for (DeliveryReadyMessage.Item item : warehouse.items()) {
                // Create a unique key for this item (warehouseId-productId)
                String itemKey = warehouse.warehouseId() + "-" + item.productId();
                
                // Skip if this item has already been lost
                if (lostItems.contains(itemKey)) {
                    continue;
                }
                
                // 5% chance of losing each item (changed from 0.95 for testing back to 0.05)
                if (random.nextDouble() < 0.05) {
                    Integer quantityLost = item.quantity() != null ? item.quantity() : 1;
                    
                    // Mark this item as lost to prevent losing it again
                    lostItems.add(itemKey);
                    
                    log.warn("--------[lost]-------- Item lost: orderId={}, warehouseId={}, productId={}, quantityLost={}", 
                            orderId, warehouse.warehouseId(), item.productId(), quantityLost);
                    
                    try {
                        DeliveryItemLostMessage message = new DeliveryItemLostMessage(
                            orderId,
                            carrier,
                            trackingCode,
                            warehouse.warehouseId(),
                            item.productId(),
                            quantityLost,
                            now,
                            correlationId
                        );
                        String payload = objectMapper.writeValueAsString(message);
                        OutboxEvent outboxEvent = new OutboxEvent(
                            "DELIVERY",
                            deliveryId,
                            "DeliveryItemLost",
                            payload,
                            correlationId
                        );
                        outboxEventRepo.save(outboxEvent);
                        log.info("--------[lost]-------- Created outbox event for lost item: orderId={}, productId={}, quantity={}", 
                                orderId, item.productId(), quantityLost);
                    } catch (Exception e) {
                        log.error("Failed to create outbox event for lost item: {}", e.getMessage(), e);
                        throw new RuntimeException("Failed to create outbox event for lost item", e);
                    }
                }
            }
        }
    }

}

