# Messaging Specification

## Overview

- **Broker**: RabbitMQ  
- **Connection defaults**: `spring.rabbitmq.*` in `application.properties` (`host=localhost`, `port=5672`, `username=admin`, `password=admin`). When running inside the compose network use `rabbitmq` as host.  
- **Serialization**: JSON via Jackson (`Jackson2JsonMessageConverter` configured in `RabbitConfig`).  
- **Queue provisioning**: Spring’s `RabbitAdmin` creates queues defined as `@Bean Queue …` in `RabbitConfig`. Every queue name can be overridden with `store.queue.*` properties.

All messages flow through the outbox pattern:

1. Domain services persist `OutboxEvent` rows with appropriate payload and `publishAt` timestamp.  
2. `OutboxRelay` polls pending rows, deserialises into the message record, publishes to RabbitMQ, then deletes the outbox entry.  
3. Consumers use `@RabbitListener` to receive the JSON payload mapped onto the corresponding Java record.

## Queues and Payloads

### Order Workflow

Messages are listed in the order the order saga publishes them.

#### `order-placed` (`store.queue.order-placed`, default `order-placed`)
- **Producer**: `OrderService.persistOutboxEvent`
- **Consumer**: `OrderWorkflowListener.handleOrderPlaced`
- **Payload** (`OrderPlacedMessage`)
```json
{ "orderId": 42, "status": "NEW", "correlationId": "UUID" }
```

#### `order-allocated` (`store.queue.order-allocated`, default `order-allocated`)
- **Producer**: `OrderSaga.persistOrderAllocatedEvent`
- **Consumer**: `OrderAllocationListener.handleOrderAllocated`
- **Payload** (`OrderAllocatedMessage`)
```json
{ "orderId": 42, "status": "ALLOCATED", "correlationId": "ORDER-42" }
```

### Payment Workflow

Messages follow the typical lifecycle from payment request through result and optional refund.

#### `payment-requested` (`store.queue.payment-requested`, default `payment-requested`)
- **Producer**: `PaymentService.requestPayment`
- **Consumer**: BankService
- **Payload** (`PaymentRequestedMessage`)
```json
{
  "customerId": 1,
  "orderId": 42,
  "paymentId": 1001,
  "amount": 125.50,
  "paymentStatus": "PENDING",
  "correlationId": "ORDER-42",
  "idempotencyKey": "42"
}
```

#### `payment-result` (`store.queue.payment-result`, default `payment-result`)
- **Producer**: Bank Service
- **Consumer**: `PaymentResultListener.handlePaymentResult`
- **Payload** (`PaymentResultMessage`)
```json
{
  "orderId": 42,
  "paymentId": 1001,
  "status": "SUCCESS",
  "bankTransactionReference": "BANKREF123",
  "failureReason": null
}
```

#### `payment-refund` (`store.queue.payment-refund`, default `payment-refund`)
- **Producer**: `RefundService.requestRefund`
- **Consumer**: BankService
- **Payload** (`PaymentRefundMessage`)
```json
{
  "orderId": 42,
  "paymentId": 1001,
  "refundId": 501,
  "amount": 125.50,
  "status": "REQUESTED",
  "correlationId": "ORDER-42"
}
```

#### `payment-refund-result` (`store.queue.payment-refund-result`, default `payment-refund-result`)
- **Producer**: BankService
- **Consumer**: `PaymentRefundResultListener.handleRefundResult`
- **Payload** (`PaymentRefundResultMessage`)
```json
{
  "orderId": 42,
  "paymentId": 1001,
  "refundId": 501,
  "status": "COMPLETED",
  "bankRefundReference": "BANKREF-REFUND-123",
  "failureReason": null,
  "correlationId": "ORDER-42"
}
```

### Delivery Workflow

Messages trace the external carrier journey from handoff through final delivery.

#### `delivery-ready` (`store.queue.delivery-ready`, default `delivery-ready`)
- **Producer**: `OrderSaga.persistOrderReadyForPickupEvent` (published after configurable delay via `publishAt`)
- **Consumer**: DeliveryCo 
- **Payload** (`OrderReadyForPickupMessage`)
```json
{
  "orderId": 42,
  "orderStatus": "PAID",
  "paymentId": 1001,
  "paymentStatus": "CONFIRMED",
  "correlationId": "ORDER-42",
  "warehouses": [
    {
      "warehouseId": 10,
      "items": [
        { "productId": 101, "quantity": 2 },
        { "productId": 103, "quantity": 1 }
      ]
    }
  ]
}
```

#### `delivery-ack` (`store.queue.delivery-ack`, default `delivery-ack`)
- **Producer**: DeliveryCo 
- **Consumer**: `DeliveryAcknowledgementListener.handleDeliveryAcknowledgement`
- **Payload** (`DeliveryAcknowledgementMessage`)
```json
{
  "orderId": 42,
  "status": "ACKNOWLEDGED",
  "carrier": "DeliveryCo",
  "trackingCode": "TRACK123",
  "acknowledgedAt": "2024-05-01T11:00:00"
}
```

#### `delivery-picked` (`store.queue.delivery-picked`, default `delivery-picked`)
- **Producer**: DeliveryCo
- **Consumer**: `DeliveryPickupListener.handleDeliveryPickup`
- **Payload** (`DeliveryPickupMessage`)
```json
{
  "orderId": 42,
  "carrier": "DeliveryCo",
  "trackingCode": "TRACK123",
  "pickedUpAt": "2024-05-01T12:15:00"
}
```

#### `delivery-in-transit` (`store.queue.delivery-in-transit`, default `delivery-in-transit`)
- **Producer**: DeliveryCo
- **Consumer**: `DeliveryInTransitListener.handleDeliveryInTransit`
- **Payload** (`DeliveryInTransitMessage`)
```json
{
  "orderId": 42,
  "carrier": "DeliveryCo",
  "trackingCode": "TRACK123",
  "eta": "2024-05-02",
  "updatedAt": "2024-05-01T18:00:00"
}
```

#### `delivery-delivered` (`store.queue.delivery-delivered`, default `delivery-delivered`)
- **Producer**: DeliveryCo
- **Consumer**: `DeliveryDeliveredListener.handleDeliveryDelivered`
- **Payload** (`DeliveryDeliveredMessage`)
```json
{
  "orderId": 42,
  "carrier": "DeliveryCo",
  "trackingCode": "TRACK123",
  "deliveredAt": "2024-05-02T09:30:00"
}
```

#### `delivery-lost` (`store.queue.delivery-lost`, default `delivery-lost`)
- **Producer**: DeliveryCo
- **Consumer**: `DeliveryItemLostListener.handleDeliveryItemLost`
- **Payload** (`DeliveryItemLostMessage`)
```json
{
  "orderId": 42,
  "carrier": "DeliveryCo",
  "trackingCode": "TRACK123",
  "warehouseId": 10,
  "productId": 101,
  "quantityLost": 1,
  "reportedAt": "2024-05-01T19:45:00",
  "correlationId": "ORDER-42"
}
```

### Email Notifications

Notifications share a queue; payload templates are ordered by when they are usually triggered.

#### `notification-email` (`store.queue.notification-email`, default `notification-email`)
- **Producer**: store-system
- **Consumer**: EmailService
Shared queue for all customer-facing email notifications. Email Service must handle these payloads:
1. **Payment result** (`PaymentResultEmailMessage`)
   ```json
   {
     "orderId": 42,
     "paymentId": 1001,
     "customerEmail": "alice@example.com",
     "status": "CONFIRMED",
     "bankTransactionReference": "BANKREF123",
     "failureReason": null,
     "correlationId": "ORDER-42"
   }
   ```
2. **Refund status** (`RefundStatusEmailMessage`)
   ```json
   {
     "orderId": 42,
     "refundId": 501,
     "customerEmail": "alice@example.com",
     "amount": 125.50,
     "status": "REQUESTED",
     "failureReason": null,
     "correlationId": "ORDER-42"
   }
   ```
3. **Delivery picked up** (`DeliveryPickupEmailMessage`)
   ```json
   {
     "orderId": 42,
     "customerEmail": "alice@example.com",
     "carrier": "DeliveryCo",
     "trackingCode": "TRACK123",
     "pickedUpAt": "2024-05-01T12:15:00",
     "status": "OUT_FOR_DELIVERY",
     "correlationId": "ORDER-42"
   }
   ```
4. **Delivery in transit** (`DeliveryInTransitEmailMessage`)
   ```json
   {
     "orderId": 42,
     "customerEmail": "alice@example.com",
     "carrier": "DeliveryCo",
     "trackingCode": "TRACK123",
     "eta": "2024-05-02",
     "status": "IN_TRANSIT",
     "updatedAt": "2024-05-01T18:00:00",
     "correlationId": "ORDER-42"
   }
   ```
5. **Delivery delivered** (`DeliveryDeliveredEmailMessage`)
   ```json
   {
     "orderId": 42,
     "customerEmail": "alice@example.com",
     "carrier": "DeliveryCo",
     "trackingCode": "TRACK123",
     "deliveredAt": "2024-05-02T09:30:00",
     "status": "DELIVERED",
     "correlationId": "ORDER-42"
   }
   ```

## Configuration Reference

Override queue names in `application.properties`:
```properties
store.queue.order-placed=custom.order.placed.queue
store.queue.notification-email=notifications.email.queue
store.queue.payment-refund-result=refund.results.queue
store.queue.delivery-lost=delivery.lost.queue
...
```

Ensure new message types have:
1. A `Queue` bean in `RabbitConfig` (so `RabbitAdmin` creates it).  
2. Matching `@Value` injection in `OutboxRelay` and any listeners.  
3. A Java record describing the JSON schema (under `com.example.store.messaging`).  
4. Outbox writer logic that serialises that record with `ObjectMapper`.
