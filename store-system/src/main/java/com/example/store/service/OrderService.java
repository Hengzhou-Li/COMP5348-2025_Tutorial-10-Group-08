package com.example.store.service;

import com.example.store.api.dto.AddOrderItemRequest;
import com.example.store.api.dto.AddOrderItemResponse;
import com.example.store.api.dto.CreateOrderItemRequest;
import com.example.store.api.dto.CreateOrderRequest;
import com.example.store.api.dto.CreateOrderResponse;
import com.example.store.api.dto.CustomerOrderItemResponse;
import com.example.store.api.dto.CustomerOrderResponse;
import com.example.store.api.dto.ReduceOrderItemRequest;
import com.example.store.api.dto.ReduceOrderItemResponse;
import com.example.store.model.Customer;
import com.example.store.model.CustomerOrder;
import com.example.store.model.OrderItem;
import com.example.store.model.OrderItemId;
import com.example.store.model.OutboxEvent;
import com.example.store.model.Product;
import com.example.store.repository.CustomerRepo;
import com.example.store.repository.OrderItemRepo;
import com.example.store.repository.OrderRepo;
import com.example.store.repository.OutboxEventRepo;
import com.example.store.repository.ProductRepo;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepo orderRepo;
    private final OrderItemRepo orderItemRepo;
    private final OutboxEventRepo outboxEventRepo;
    private final CustomerRepo customerRepo;
    private final ProductRepo productRepo;

    public OrderService(
            OrderRepo orderRepo,
            OrderItemRepo orderItemRepo,
            OutboxEventRepo outboxEventRepo,
            CustomerRepo customerRepo,
            ProductRepo productRepo) {
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.outboxEventRepo = outboxEventRepo;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    @Transactional
    public AddOrderItemResponse addOrderItem(Integer orderId, AddOrderItemRequest request) {
        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        Product product = productRepo.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        OrderItemId orderItemId = new OrderItemId();
        orderItemId.setOrderId(orderId);
        orderItemId.setProductId(product.getId());

        OrderItem orderItem = orderItemRepo.findById(orderItemId).orElse(null);
        int quantityToAdd = request.quantity();

        if (orderItem == null) {
            orderItem = new OrderItem();
            orderItem.setId(orderItemId);
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setUnitPrice(product.getUnitPrice());
            orderItem.setQuantity(quantityToAdd);
            order.getItems().add(orderItem);
        } else {
            orderItem.setQuantity(orderItem.getQuantity() + quantityToAdd);
        }

        BigDecimal additionalTotal = product.getUnitPrice()
                .multiply(BigDecimal.valueOf(quantityToAdd));
        BigDecimal currentTotal = order.getOrderTotal() != null
                ? order.getOrderTotal()
                : BigDecimal.ZERO;
        order.setOrderTotal(currentTotal.add(additionalTotal));
        order.setUpdatedAt(LocalDateTime.now());

        orderRepo.save(order);
        OrderItem savedItem = orderItemRepo.save(orderItem);

        return new AddOrderItemResponse(
                order.getId(),
                product.getId(),
                savedItem.getQuantity(),
                order.getOrderTotal());
    }

    @Transactional
    public ReduceOrderItemResponse reduceOrderItem(Integer orderId, ReduceOrderItemRequest request) {
        CustomerOrder order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));

        OrderItemId orderItemId = new OrderItemId();
        orderItemId.setOrderId(orderId);
        orderItemId.setProductId(request.productId());

        OrderItem orderItem = orderItemRepo.findById(orderItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order item not found"));

        int existingQuantity = orderItem.getQuantity();
        int quantityToRemove = request.quantity();

        if (quantityToRemove > existingQuantity) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot reduce quantity below zero");
        }

        BigDecimal unitPrice = orderItem.getUnitPrice();
        BigDecimal reductionTotal = unitPrice.multiply(BigDecimal.valueOf(quantityToRemove));
        BigDecimal currentTotal = order.getOrderTotal() != null ? order.getOrderTotal() : BigDecimal.ZERO;
        order.setOrderTotal(currentTotal.subtract(reductionTotal).max(BigDecimal.ZERO));
        order.setUpdatedAt(LocalDateTime.now());

        int remainingQuantity = existingQuantity - quantityToRemove;
        if (remainingQuantity == 0) {
            order.getItems().remove(orderItem);
            orderItemRepo.delete(orderItem);
        } else {
            orderItem.setQuantity(remainingQuantity);
            orderItemRepo.save(orderItem);
        }

        orderRepo.save(order);

        return new ReduceOrderItemResponse(order.getId(), request.productId(), remainingQuantity, order.getOrderTotal());
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderResponse> getOrdersForCustomer(Integer customerId) {
        if (!customerRepo.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }

        List<CustomerOrder> orders = orderRepo.findByCustomerId(customerId);
        return orders.stream()
                .map(this::toCustomerOrderResponse)
                .toList();
    }

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request) {
        var customerId = request.customerId();
        var itemRequests = request.items();

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        if (itemRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must contain at least one item");
        }

        ensureUniqueProducts(itemRequests);

        Map<Integer, Product> productsById = fetchProducts(itemRequests);

        LocalDateTime now = LocalDateTime.now();
        CustomerOrder order = buildNewOrder(customer, itemRequests, productsById, now);
        order = orderRepo.save(order);

        List<OrderItem> orderItems = buildOrderItems(order, itemRequests, productsById);
        orderItemRepo.saveAll(orderItems);

        String correlationId = UUID.randomUUID().toString();
        persistOutboxEvent(order, correlationId, now);

        log.info("Created order {} for customer {} with {} items (correlationId={})",
                order.getId(),
                customerId,
                itemRequests.size(),
                correlationId);

        return new CreateOrderResponse(order.getId(), order.getStatus(), correlationId);
    }

    private void ensureUniqueProducts(List<CreateOrderItemRequest> itemRequests) {
        Set<Integer> productIds = itemRequests.stream()
                .map(CreateOrderItemRequest::productId)
                .collect(Collectors.toSet());

        if (productIds.size() != itemRequests.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate products are not allowed.");
        }
    }

    private Map<Integer, Product> fetchProducts(List<CreateOrderItemRequest> itemRequests) {
        List<Integer> productIds = itemRequests.stream()
                .map(CreateOrderItemRequest::productId)
                .toList();

        List<Product> products = productRepo.findAllById(productIds);
        if (products.size() != productIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more products were not found.");
        }

        Map<Integer, Product> productsById = new HashMap<>();
        for (Product product : products) {
            productsById.put(product.getId(), product);
        }
        return productsById;
    }

    private CustomerOrder buildNewOrder(
            Customer customer,
            List<CreateOrderItemRequest> itemRequests,
            Map<Integer, Product> productsById,
            LocalDateTime now) {
        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setStatus("NEW");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setOrderTotal(calculateOrderTotal(itemRequests, productsById));
        return order;
    }

    private BigDecimal calculateOrderTotal(
            List<CreateOrderItemRequest> itemRequests, Map<Integer, Product> productsById) {
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItemRequest itemRequest : itemRequests) {
            Product product = productsById.get(itemRequest.productId());
            BigDecimal lineTotal = product.getUnitPrice()
                    .multiply(BigDecimal.valueOf(itemRequest.quantity()));
            total = total.add(lineTotal);
        }
        return total;
    }

    private List<OrderItem> buildOrderItems(
            CustomerOrder order,
            List<CreateOrderItemRequest> itemRequests,
            Map<Integer, Product> productsById) {

        List<OrderItem> orderItems = new ArrayList<>(itemRequests.size());
        for (CreateOrderItemRequest itemRequest : itemRequests) {
            Product product = productsById.get(itemRequest.productId());

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemRequest.quantity());
            orderItem.setUnitPrice(product.getUnitPrice());
            orderItem.getId().setOrderId(order.getId());
            orderItem.getId().setProductId(product.getId());

            order.getItems().add(orderItem);
            orderItems.add(orderItem);
        }
        return orderItems;
    }

    private void persistOutboxEvent(CustomerOrder order, String correlationId, LocalDateTime timestamp) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(order.getId());
        event.setEventType("OrderPlaced");
        event.setCorrelationId(correlationId);
        event.setCreatedAt(timestamp);
        event.setPublishAt(timestamp);
        event.setPayload(buildOrderPlacedPayload(order, correlationId));
        outboxEventRepo.save(event);
    }

    private String buildOrderPlacedPayload(CustomerOrder order, String correlationId) {
        return String.format(
                "{\"orderId\":%d,\"status\":\"%s\",\"correlationId\":\"%s\"}",
                order.getId(), order.getStatus(), correlationId);
    }

    private CustomerOrderResponse toCustomerOrderResponse(CustomerOrder order) {
        List<CustomerOrderItemResponse> items = order.getItems().stream()
                .map(this::toCustomerOrderItemResponse)
                .toList();
        return new CustomerOrderResponse(
                order.getId(),
                order.getOrderTotal(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items);
    }

    private CustomerOrderItemResponse toCustomerOrderItemResponse(OrderItem item) {
        Product product = item.getProduct();
        return new CustomerOrderItemResponse(
                product != null ? product.getId() : null,
                product != null ? product.getSku() : null,
                product != null ? product.getName() : null,
                item.getQuantity(),
                item.getUnitPrice());
    }
}
