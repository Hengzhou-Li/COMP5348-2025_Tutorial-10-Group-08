package com.example.store.repository;

import com.example.store.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepo extends JpaRepository<Warehouse, Integer> {
}
