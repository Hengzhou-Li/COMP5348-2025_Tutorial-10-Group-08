package com.example.store.repository;

import com.example.store.model.Product;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepo extends JpaRepository<Product, Integer> {

    Optional<Product> findBySku(String sku);
}
