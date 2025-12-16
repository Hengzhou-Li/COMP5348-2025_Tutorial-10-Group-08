package com.example.deliveryco_service.repository;

import com.example.deliveryco_service.model.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepo extends JpaRepository<Delivery, Integer> {
    
    Optional<Delivery> findByOrderId(Integer orderId);
    
}


