package com.example.store.repository;

import com.example.store.model.FulfillmentItem;
import com.example.store.model.FulfillmentItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentItemRepo extends JpaRepository<FulfillmentItem, FulfillmentItemId> {
}
