package com.example.store.repository;

import com.example.store.model.StockLedger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockLedgerRepo extends JpaRepository<StockLedger, Integer> {
}
