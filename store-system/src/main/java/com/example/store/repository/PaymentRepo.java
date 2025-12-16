package com.example.store.repository;

import com.example.store.model.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepo extends JpaRepository<Payment, Integer> {

    @Query("select p from Payment p where p.bankTransactionReference = :ref")
    Optional<Payment> findByBankTxnRef(@Param("ref") String ref);
}
