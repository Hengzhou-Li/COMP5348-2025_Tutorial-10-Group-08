package com.example.store.repository;

import com.example.store.model.WarehouseStock;
import com.example.store.model.WarehouseStockId;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseStockRepo extends JpaRepository<WarehouseStock, WarehouseStockId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select ws from WarehouseStock ws "
                    + "where ws.id.warehouseId = :warehouseId and ws.id.productId = :productId")
    Optional<WarehouseStock> lockRow(
            @Param("warehouseId") Integer warehouseId, @Param("productId") Integer productId);

    List<WarehouseStock> findByIdProductId(Integer productId);
}
