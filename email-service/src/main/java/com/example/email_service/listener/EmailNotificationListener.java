package com.example.email_service.listener;

import com.example.email_service.messaging.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationListener.class);

    private final ObjectMapper objectMapper;
    private final String queueName;

    public EmailNotificationListener(
            ObjectMapper objectMapper,
            @Value("${store.queue.notification-email:notification-email}") String queueName) {
        this.objectMapper = objectMapper;
        this.queueName = queueName;
    }

    @RabbitListener(queues = "${store.queue.notification-email:notification-email}")
    public void handleEmailNotification(byte[] payload) {
        log.info("--------[email]-------- Received email notification on queue '{}'", queueName);
        
        try {
            String jsonPayload = new String(payload);
            JsonNode jsonNode = objectMapper.readTree(jsonPayload);
            
            // Determine message type based on jsonNode

            //print the jsonnode as a string for debugging
            log.info("JSON Node: {}", jsonNode.toString());
            String messageType = determineMessageType(jsonNode);
            
            switch (messageType) {
                case "PAYMENT_RESULT":
                    PaymentResultEmailMessage paymentResult = objectMapper.treeToValue(
                        jsonNode, PaymentResultEmailMessage.class);
                    handlePaymentResult(paymentResult);
                    break;
                    
                case "REFUND_STATUS":
                    RefundStatusEmailMessage refundStatus = objectMapper.treeToValue(
                        jsonNode, RefundStatusEmailMessage.class);
                    handleRefundStatus(refundStatus);
                    break;
                    
                case "DELIVERY_PICKUP":
                    DeliveryPickupEmailMessage deliveryPickup = objectMapper.treeToValue(
                        jsonNode, DeliveryPickupEmailMessage.class);
                    handleDeliveryPickup(deliveryPickup);
                    break;
                    
                case "DELIVERY_IN_TRANSIT":
                    DeliveryInTransitEmailMessage deliveryInTransit = objectMapper.treeToValue(
                        jsonNode, DeliveryInTransitEmailMessage.class);
                    handleDeliveryInTransit(deliveryInTransit);
                    break;
                    
                case "DELIVERY_DELIVERED":
                    DeliveryDeliveredEmailMessage deliveryDelivered = objectMapper.treeToValue(
                        jsonNode, DeliveryDeliveredEmailMessage.class);
                    handleDeliveryDelivered(deliveryDelivered);
                    break;
                    
                case "LOST_ITEM":
                    DeliveryItemLostEmailMessage lostItem = objectMapper.treeToValue(
                        jsonNode, DeliveryItemLostEmailMessage.class);
                    handleLostItem(lostItem);
                    break;
                    
                default:
                    log.warn("--------[email]-------- Unknown email notification type. Raw payload: {}", jsonPayload);
                    break;
            }

        } catch (Exception e) {
            log.error("--------[email]-------- Error processing email notification: {}", e.getMessage(), e);
        }
    }
    
    private String determineMessageType(JsonNode jsonNode) {
        // Check for unique distinguishing fields for each message type
        if (jsonNode.has("paymentId") && jsonNode.has("bankTransactionReference")) {
            return "PAYMENT_RESULT";
        }
        if (jsonNode.has("refundId") && jsonNode.has("amount")) {
            return "REFUND_STATUS";
        }
        if (jsonNode.has("pickedUpAt")) {
            return "DELIVERY_PICKUP";
        }
        if (jsonNode.has("eta") && jsonNode.has("updatedAt")) {
            return "DELIVERY_IN_TRANSIT";
        }
        if (jsonNode.has("deliveredAt")) {
            return "DELIVERY_DELIVERED";
        }
        if (jsonNode.has("productId") && jsonNode.has("quantityLost") && jsonNode.has("warehouseId")) {
            return "LOST_ITEM";
        }
        return "UNKNOWN";
    }

    private void handlePaymentResult(PaymentResultEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Payment Result");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Payment ID: " + message.paymentId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Status: " + message.status());
        System.out.println("Bank Transaction Reference: " + message.bankTransactionReference());
        System.out.println("Failure Reason: " + (message.failureReason() != null ? message.failureReason() : "N/A"));
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private void handleRefundStatus(RefundStatusEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Refund Status");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Refund ID: " + message.refundId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Amount: $" + message.amount());
        System.out.println("Status: " + message.status());
        System.out.println("Failure Reason: " + (message.failureReason() != null ? message.failureReason() : "N/A"));
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private void handleDeliveryPickup(DeliveryPickupEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Delivery Picked Up");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Carrier: " + message.carrier());
        System.out.println("Tracking Code: " + message.trackingCode());
        System.out.println("Picked Up At: " + message.pickedUpAt());
        System.out.println("Status: " + message.status());
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private void handleDeliveryInTransit(DeliveryInTransitEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Delivery In Transit");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Carrier: " + message.carrier());
        System.out.println("Tracking Code: " + message.trackingCode());
        System.out.println("ETA: " + message.eta());
        System.out.println("Status: " + message.status());
        System.out.println("Updated At: " + message.updatedAt());
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private void handleDeliveryDelivered(DeliveryDeliveredEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Delivery Delivered");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Carrier: " + message.carrier());
        System.out.println("Tracking Code: " + message.trackingCode());
        System.out.println("Delivered At: " + message.deliveredAt());
        System.out.println("Status: " + message.status());
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }

    private void handleLostItem(DeliveryItemLostEmailMessage message) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("--------[email]-------- EMAIL NOTIFICATION: Lost Item");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Order ID: " + message.orderId());
        System.out.println("Customer Email: " + message.customerEmail());
        System.out.println("Carrier: " + message.carrier());
        System.out.println("Tracking Code: " + message.trackingCode());
        System.out.println("Warehouse ID: " + message.warehouseId());
        System.out.println("Product ID: " + message.productId());
        System.out.println("Quantity Lost: " + message.quantityLost());
        System.out.println("Reported At: " + message.reportedAt());
        System.out.println("Correlation ID: " + message.correlationId());
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
    }
}
