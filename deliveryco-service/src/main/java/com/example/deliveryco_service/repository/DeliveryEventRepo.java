package com.example.deliveryco_service.repository;

import com.example.deliveryco_service.model.DeliveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryEventRepo extends JpaRepository<DeliveryEvent, Integer> {
    
    List<DeliveryEvent> findByDeliveryIdOrderByEventTimeAsc(Integer deliveryId);
    
}


