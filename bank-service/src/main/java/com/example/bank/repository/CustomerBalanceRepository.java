package com.example.bank.repository;

import com.example.bank.entity.CustomerBalance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerBalanceRepository extends JpaRepository<CustomerBalance, Integer> {
}
