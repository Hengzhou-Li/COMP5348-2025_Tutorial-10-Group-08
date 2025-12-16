package com.example.store.repository;

import com.example.store.model.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryEventRepo extends JpaRepository<DeliveryEvent, Integer> {
}
