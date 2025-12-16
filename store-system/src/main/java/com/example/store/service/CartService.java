package com.example.store.service;

import com.example.store.api.dto.AddCartItemRequest;
import com.example.store.api.dto.CartItemResponse;
import com.example.store.api.dto.CartResponse;
import com.example.store.api.dto.CreateOrderItemRequest;
import com.example.store.api.dto.CreateOrderRequest;
import com.example.store.api.dto.CreateOrderResponse;
import com.example.store.api.dto.UpdateCartItemRequest;
import com.example.store.model.Customer;
import com.example.store.model.Product;
import com.example.store.repository.CustomerRepo;
import com.example.store.repository.ProductRepo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {

  private final CustomerRepo customerRepo;
  private final ProductRepo productRepo;
  private final OrderService orderService;

  private final Map<Integer, Map<Integer, Integer>> carts = new ConcurrentHashMap<>();

  public CartService(CustomerRepo customerRepo, ProductRepo productRepo, OrderService orderService) {
    this.customerRepo = customerRepo;
    this.productRepo = productRepo;
    this.orderService = orderService;
  }

  @Transactional(readOnly = true)
  public CartResponse getCart(Integer customerId) {
    Customer customer = fetchCustomer(customerId);
    return buildCartResponse(customer.getId());
  }

  @Transactional
  public CartResponse addItem(Integer customerId, AddCartItemRequest request) {
    Customer customer = fetchCustomer(customerId);
    Product product = fetchProduct(request.productId());

    carts.computeIfAbsent(customer.getId(), id -> new ConcurrentHashMap<>())
        .merge(product.getId(), request.quantity(), Integer::sum);

    return buildCartResponse(customer.getId());
  }

  @Transactional
  public CartResponse updateItem(Integer customerId, Integer productId, UpdateCartItemRequest request) {
    Customer customer = fetchCustomer(customerId);
    Product product = fetchProduct(productId);

    carts.computeIfAbsent(customer.getId(), id -> new ConcurrentHashMap<>())
        .put(product.getId(), request.quantity());
    return buildCartResponse(customer.getId());
  }

  @Transactional
  public CartResponse removeItem(Integer customerId, Integer productId) {
    Customer customer = fetchCustomer(customerId);
    carts.computeIfPresent(customer.getId(), (id, items) -> {
      items.remove(productId);
      if (items.isEmpty()) {
        return null;
      }
      return items;
    });
    return buildCartResponse(customer.getId());
  }

  @Transactional
  public CartResponse clearCart(Integer customerId) {
    Customer customer = fetchCustomer(customerId);
    carts.remove(customer.getId());
    return buildCartResponse(customer.getId());
  }

  @Transactional
  public CreateOrderResponse checkout(Integer customerId) {
    Customer customer = fetchCustomer(customerId);
    Map<Integer, Integer> cartItems = new ConcurrentHashMap<>(carts.getOrDefault(customer.getId(), Map.of()));
    if (cartItems.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty.");
    }

    List<CartLine> lines = resolveCartLines(customer.getId(), cartItems);
    if (lines.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty.");
    }

    List<CreateOrderItemRequest> orderItems = lines.stream()
        .map(line -> new CreateOrderItemRequest(line.product().getId(), line.quantity()))
        .toList();

    CreateOrderRequest request = new CreateOrderRequest(customer.getId(), orderItems);
    CreateOrderResponse response = orderService.createOrder(request);

    carts.remove(customer.getId());
    return response;
  }

  private Customer fetchCustomer(Integer customerId) {
    return customerRepo.findById(customerId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
  }

  private Product fetchProduct(Integer productId) {
    return productRepo.findById(productId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  private CartResponse buildCartResponse(Integer customerId) {
    Map<Integer, Integer> snapshot = carts.getOrDefault(customerId, Map.of());
    List<CartLine> lines = resolveCartLines(customerId, snapshot);

    List<CartItemResponse> items = lines.stream()
        .map(line -> {
          BigDecimal lineTotal = line.product().getUnitPrice()
              .multiply(BigDecimal.valueOf(line.quantity()));
          return new CartItemResponse(
              line.product().getId(),
              line.product().getSku(),
              line.product().getName(),
              line.product().getUnitPrice(),
              line.quantity(),
              lineTotal);
        })
        .toList();

    BigDecimal total = items.stream()
        .map(CartItemResponse::lineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new CartResponse(customerId, items, total);
  }

  private List<CartLine> resolveCartLines(Integer customerId, Map<Integer, Integer> cartItems) {
    if (cartItems.isEmpty()) {
      return List.of();
    }

    Map<Integer, Integer> snapshot = new ConcurrentHashMap<>(cartItems);
    List<Product> products = productRepo.findAllById(snapshot.keySet());
    Map<Integer, Product> productsById = products.stream()
        .collect(Collectors.toMap(Product::getId, p -> p));

    List<CartLine> lines = new ArrayList<>();
    for (Map.Entry<Integer, Integer> entry : snapshot.entrySet()) {
      Product product = productsById.get(entry.getKey());
      if (product == null) {
        carts.computeIfPresent(customerId, (id, items) -> {
          items.remove(entry.getKey());
          return items.isEmpty() ? null : items;
        });
        continue;
      }
      Integer quantity = entry.getValue();
      if (quantity == null || quantity <= 0) {
        carts.computeIfPresent(customerId, (id, items) -> {
          items.remove(entry.getKey());
          return items.isEmpty() ? null : items;
        });
        continue;
      }
      lines.add(new CartLine(product, quantity));
    }
    return lines;
  }

  private record CartLine(Product product, Integer quantity) {
  }
}