package com.example.bank.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "customer_balance")
public class CustomerBalance {

    @Id
    private Integer customerId;
    private BigDecimal balance;

    public CustomerBalance() {}

    public CustomerBalance(Integer customerId, BigDecimal balance) {
        this.customerId = customerId;
        this.balance = balance;
    }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
