package com.example.store.repository;

import com.example.store.model.CustomerOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepo extends JpaRepository<CustomerOrder, Integer> {

    List<CustomerOrder> findByCustomerId(Integer customerId);

    @Query("SELECT o FROM CustomerOrder o JOIN FETCH o.customer WHERE o.id = :orderId")
    Optional<CustomerOrder> findByIdWithCustomer(@Param("orderId") Integer orderId);
}
