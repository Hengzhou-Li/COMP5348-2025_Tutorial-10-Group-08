package com.example.bank.config;

import com.example.bank.entity.CustomerBalance;
import com.example.bank.repository.CustomerBalanceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataInitializer implements CommandLineRunner {

    private final CustomerBalanceRepository repository;

    public DataInitializer(CustomerBalanceRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        repository.save(new CustomerBalance(1, BigDecimal.valueOf(1000.00)));
        repository.save(new CustomerBalance(2, BigDecimal.valueOf(500.00)));
        System.out.println("Initialized customer balances.");
    }
}
