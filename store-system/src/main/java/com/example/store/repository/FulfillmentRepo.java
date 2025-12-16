package com.example.store.repository;

import com.example.store.model.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentRepo extends JpaRepository<Fulfillment, Integer> {
}
