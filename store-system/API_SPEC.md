# Store System HTTP API Specification

This document describes the REST APIs exposed by the store system Spring Boot service. Unless stated otherwise, all endpoints:

- are prefixed with `/api`
- expect and return JSON (`Content-Type: application/json`)
- use UTF-8 encoding
- follow Spring Boot's default RFC 7807 `application/problem+json` error format for non-2xx responses

## Error Handling Conventions

When a request fails, the service returns an RFC&nbsp;7807 problem response similar to:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Customer not found",
  "instance": "/api/orders"
}
```

Validation failures (triggered by `@Valid`/`jakarta.validation` annotations) also respond with HTTP&nbsp;400 and include details about the field violations in the `detail` (and, depending on Spring Boot configuration, an `errors` array).

## Endpoint Summary

| Method | Path                                      | Description                                    | Remark             |
|--------|-------------------------------------------|------------------------------------------------|--------------------|
| GET    | `/api/products`                           | List all products                              |                    |
| GET    | `/api/customers/{customerId}/orders`      | List orders placed by a customer               |                    |
| POST   | `/api/orders`                             | Create a new order                             |                    |
| POST   | `/api/orders/{orderId}/items`             | Add a product to an order or increase quantity |                    |
| POST   | `/api/orders/{orderId}/items/reduce`      | Reduce quantity for a product in an order      |                    |
| POST   | `/api/orders/{orderId}/reserve`           | Allocate stock for an order                    | Testing/internal   |
| POST   | `/api/orders/{orderId}/payment`           | Request payment for an allocated order         |                    |
| POST   | `/api/orders/{orderId}/payment/bypass-success` | Force payment success and trigger delivery | Testing only       |
| POST   | `/api/orders/{orderId}/cancel`            | Cancel an order (and trigger refund if needed) |                    |

---

## GET /api/products

Returns every product currently stored in the catalogue.

### Response — 200 OK

`application/json`

```json
[
  {
    "id": 42,
    "sku": "BLU-TSHIRT-XL",
    "name": "Blue T-Shirt XL",
    "description": "100% cotton tee",
    "unitPrice": 29.95,
    "active": true
  }
]
```

### Error Responses

This endpoint currently has no bespoke error conditions; only generic server failures (500) may be returned.

---

## GET /api/customers/{customerId}/orders

Lists all orders associated with a customer. Returns an empty array if the customer exists but has not placed any orders.

### Path Parameters

| Name         | Type    | Description           |
|--------------|---------|-----------------------|
| `customerId` | Integer | Customer primary key. |

### Response — 200 OK

`application/json`

```json
[
  {
    "orderId": 1001,
    "orderTotal": 149.90,
    "status": "PAID",
    "createdAt": "2025-10-22T11:43:12.345",
    "updatedAt": "2025-10-23T09:20:04.112",
    "items": [
      {
        "productId": 42,
        "productSku": "BLU-TSHIRT-XL",
        "productName": "Blue T-Shirt XL",
        "quantity": 2,
        "unitPrice": 29.95
      }
    ]
  }
]
```

Timestamps are serialized as ISO-8601 strings (`yyyy-MM-dd'T'HH:mm:ss.SSS` by default).

### Error Responses

| Status | Condition           | Detail message           |
|--------|---------------------|--------------------------|
| 404    | Customer not found  | `Customer not found`     |

---

## POST /api/orders

Creates a new order for an existing customer and enqueues an outbox event for downstream processing.

### Request

`Content-Type: application/json`

```json
{
  "customerId": 7,
  "items": [
    {
      "productId": 42,
      "quantity": 2
    }
  ]
}
```

`items` must contain at least one entry. Each entry must reference a unique product (`productId`) and specify a strictly positive quantity.

### Response — 201 Created

```json
{
  "orderId": 1001,
  "status": "NEW",
  "correlationId": "b03b1f9e-9f73-4b2d-8083-0f2de14dcab0"
}
```

### Error Responses

| Status | Condition                                                | Detail message                                      |
|--------|----------------------------------------------------------|-----------------------------------------------------|
| 400    | Request body fails validation                            | e.g. `Invalid request content`                      |
| 400    | Order has no items (after validation)                    | `Order must contain at least one item`              |
| 400    | Duplicate product IDs supplied                           | `Duplicate products are not allowed.`               |
| 404    | Customer does not exist                                  | `Customer not found`                                |
| 404    | One or more referenced products do not exist             | `One or more products were not found.`              |

---

## POST /api/orders/{orderId}/items

Adds a new product line to an existing order. When the product is already present, the supplied quantity is added to the existing line instead of creating a duplicate entry. The order total is recalculated accordingly.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Request

`Content-Type: application/json`

```json
{
  "productId": 42,
  "quantity": 2
}
```

`quantity` must be strictly positive.

### Response — 200 OK

```json
{
  "orderId": 1001,
  "productId": 42,
  "quantity": 3,
  "orderTotal": 89.85
}
```

`quantity` represents the new total quantity for the product inside the order.

### Error Responses

| Status | Condition                 | Detail message             |
|--------|---------------------------|----------------------------|
| 400    | Request fails validation  | e.g. `Invalid request`     |
| 404    | Order does not exist      | `Order not found`          |
| 404    | Product does not exist    | `Product not found`        |

---

## POST /api/orders/{orderId}/items/reduce

Decreases the quantity of a product that already exists on the order. If the quantity drops to zero, the order line is removed entirely. The order total is reduced accordingly.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Request

`Content-Type: application/json`

```json
{
  "productId": 42,
  "quantity": 1
}
```

`quantity` must be strictly positive.

### Response — 200 OK

```json
{
  "orderId": 1001,
  "productId": 42,
  "quantity": 1,
  "orderTotal": 59.90
}
```

`quantity` represents the remaining quantity of the product on the order. If the product line is removed, `quantity` is `0`.

### Error Responses

| Status | Condition                            | Detail message                    |
|--------|--------------------------------------|-----------------------------------|
| 400    | Request fails validation             | e.g. `Invalid request`            |
| 400    | Reduction would make quantity < 0    | `Cannot reduce quantity below zero` |
| 404    | Order does not exist                 | `Order not found`                 |
| 404    | Target order item does not exist     | `Order item not found`            |

---

## POST /api/orders/{orderId}/reserve

Attempts to allocate warehouse stock for the requested order and updates its status to `ALLOCATED`.
**Note:** This endpoint exists to support integration testing and internal workflows; it is not intended for direct frontend use.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Response — 200 OK

```json
{
  "orderId": 1001,
  "status": "ALLOCATED"
}
```

### Error Responses

| Status | Condition                                             | Detail message                                                             |
|--------|-------------------------------------------------------|----------------------------------------------------------------------------|
| 400    | Order contains zero items                             | `Order has no items to allocate.`                                          |
| 404    | Order not found                                       | `Order not found.`                                                         |
| 409    | Order status is not `NEW`                             | `Order not in NEW status for allocation.`                                  |
| 409    | No warehouse has sufficient available stock           | `Insufficient stock to allocate product {sku}`                             |
| 409    | Stock became insufficient during reservation locking  | `Insufficient available stock when reserving product {sku}`                |
| 500    | Order item missing product relationship               | `Order item missing product.`                                              |
| 500    | Allocation data inconsistent (missing warehouse info) | `Allocation missing warehouse.`                                            |

---

## POST /api/orders/{orderId}/payment

Requests payment for an already allocated order. A new payment entity is created and an outbox event is published.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Response — 200 OK

```json
{
  "orderId": 1001,
  "paymentId": 555,
  "amount": 149.90,
  "paymentStatus": "PENDING",
  "orderStatus": "PAYMENT_PENDING",
  "bankTransactionReference": null
}
```

### Error Responses

| Status | Condition                                   | Detail message                                    |
|--------|---------------------------------------------|---------------------------------------------------|
| 404    | Order not found                             | `Order not found.`                                |
| 409    | Order is not in `ALLOCATED` status          | `Order not in ALLOCATED status for payment.`      |
| 409    | Payment was already created for this order  | `Payment already requested for this order.`       |

---

## POST /api/orders/{orderId}/payment/bypass-success

Marks payment as confirmed (creating one if necessary) and emits delivery-related outbox events without contacting the payment service. Intended for testing.
**Note:** Reserved for test scenarios only; frontend clients should never invoke this endpoint.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Response — 204 No Content

No response body.

### Error Responses

| Status | Condition                                     | Detail message                                                    |
|--------|-----------------------------------------------|-------------------------------------------------------------------|
| 404    | Order not found                               | `Order not found.`                                                |
| 409    | Order status is not `ALLOCATED`, `PAYMENT_PENDING`, or `PAID` | `Order not in a state that can bypass payment processing.` |

---

## POST /api/orders/{orderId}/cancel

Cancels an order, releases reserved stock, and optionally initiates a refund when payment has already been processed.

### Path Parameters

| Name      | Type    | Description        |
|-----------|---------|--------------------|
| `orderId` | Integer | Order primary key. |

### Response — 200 OK

```json
{
  "orderId": 1001,
  "orderStatus": "CANCELLED",
  "refundId": 777,
  "refundStatus": "REQUESTED"
}
```

`refundId` and `refundStatus` are `null` when no refund was triggered.

### Error Responses

| Status | Condition                                    | Detail message                                                                 |
|--------|----------------------------------------------|--------------------------------------------------------------------------------|
| 404    | Order not found                              | `Order not found.`                                                             |
| 409    | Order already cancelled                      | `Order already cancelled.`                                                     |
| 409    | Delivery request already sent / order fulfilled | `Order already sent to delivery and cannot be cancelled.`                    |
| 500    | Warehouse stock could not be located during release | `Unable to locate warehouse stock during release.`                         |

---

## Generic Error Codes

Independent of the specific endpoint, clients should be prepared to handle:

- `400 Bad Request` for malformed JSON, validation failures, or unsupported operations.
- `404 Not Found` when a referenced resource does not exist.
- `409 Conflict` when the requested state transition violates business rules.
- `500 Internal Server Error` for unexpected server-side issues (the `detail` field will include a brief message).

All error responses conform to the RFC&nbsp;7807 structure demonstrated above.
