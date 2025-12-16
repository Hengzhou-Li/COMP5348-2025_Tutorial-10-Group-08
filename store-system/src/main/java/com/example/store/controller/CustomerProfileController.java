package com.example.store.controller;

import com.example.store.api.dto.CustomerProfileResponse;
import com.example.store.model.Customer;
import com.example.store.repository.CustomerRepo;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customers")
public class CustomerProfileController {

  private final CustomerRepo customerRepo;

  public CustomerProfileController(CustomerRepo customerRepo) {
    this.customerRepo = customerRepo;
  }

  @GetMapping("/by-username/{username}")
  public CustomerProfileResponse resolveCustomer(@PathVariable String username) {
    Customer customer = customerRepo.findByAuthUsername(username)
        .orElseGet(() -> createCustomerForUsername(username));

    return new CustomerProfileResponse(customer.getId(), customer.getFullName());
  }

  private Customer createCustomerForUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
    }

    Customer customer = new Customer();
    customer.setFullName(capitalizeUsername(username));
    customer.setEmail(username + "@example.com");
    customer.setPhone(null);
    customer.setCreatedAt(LocalDateTime.now());
    customer.setAuthUsername(username);
    return customerRepo.save(customer);
  }

  private String capitalizeUsername(String username) {
    if (username.isBlank()) {
      return "Customer";
    }
    return Character.toUpperCase(username.charAt(0)) + username.substring(1);
  }
}