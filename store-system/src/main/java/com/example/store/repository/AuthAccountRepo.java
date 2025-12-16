package com.example.store.repository;

import com.example.store.model.AuthAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAccountRepo extends JpaRepository<AuthAccount, Integer> {

    Optional<AuthAccount> findByUsername(String username);
}
