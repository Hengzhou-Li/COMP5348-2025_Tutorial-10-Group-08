package com.example.store.repository;

import com.example.store.model.OrderItem;
import com.example.store.model.OrderItemId;
import com.example.store.model.CustomerOrder;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepo extends JpaRepository<OrderItem, OrderItemId> {

    List<OrderItem> findByOrder(CustomerOrder order);
}
