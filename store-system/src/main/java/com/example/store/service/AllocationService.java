package com.example.store.service;

import com.example.store.model.CustomerOrder;
import com.example.store.model.Fulfillment;
import com.example.store.model.FulfillmentItem;
import com.example.store.model.FulfillmentItemId;
import com.example.store.model.OrderItem;
import com.example.store.model.StockLedger;
import com.example.store.model.Warehouse;
import com.example.store.model.WarehouseStock;
import com.example.store.model.WarehouseStockId;
import com.example.store.repository.FulfillmentItemRepo;
import com.example.store.repository.FulfillmentRepo;
import com.example.store.repository.StockLedgerRepo;
import com.example.store.repository.WarehouseStockRepo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AllocationService {

    private final WarehouseStockRepo warehouseStockRepo;
    private final FulfillmentRepo fulfillmentRepo;
    private final FulfillmentItemRepo fulfillmentItemRepo;
    private final StockLedgerRepo stockLedgerRepo;

    public AllocationService(
            WarehouseStockRepo warehouseStockRepo,
            FulfillmentRepo fulfillmentRepo,
            FulfillmentItemRepo fulfillmentItemRepo,
            StockLedgerRepo stockLedgerRepo) {
        this.warehouseStockRepo = warehouseStockRepo;
        this.fulfillmentRepo = fulfillmentRepo;
        this.fulfillmentItemRepo = fulfillmentItemRepo;
        this.stockLedgerRepo = stockLedgerRepo;
    }

    @Transactional(readOnly = true)
    public AllocationPlan planAllocation(List<OrderItem> orderItems) {
        if (orderItems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order has no items to allocate.");
        }

        List<AllocationPlan.AllocationLine> allocations = new ArrayList<>();

        for (OrderItem item : orderItems) {
            var product = item.getProduct();
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Order item missing product.");
            }

            var stocks = warehouseStockRepo.findByIdProductId(product.getId());

            WarehouseStock chosenStock = stocks.stream()
                    .filter(stock -> {
                        Integer onHand = Objects.requireNonNullElse(stock.getQtyOnHand(), 0);
                        Integer reserved = Objects.requireNonNullElse(stock.getQtyReserved(), 0);
                        return onHand - reserved >= item.getQuantity();
                    })
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Insufficient stock to allocate product " + product.getSku()));

            allocations.add(new AllocationPlan.AllocationLine(chosenStock.getWarehouse(), product, item.getQuantity()));
        }

        return new AllocationPlan(allocations);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveStock(CustomerOrder order, AllocationPlan plan) {
        LocalDateTime now = LocalDateTime.now();

        Map<Integer, Fulfillment> fulfillmentsByWarehouse = new HashMap<>();
        List<FulfillmentItem> fulfillmentItems = new ArrayList<>();
        List<StockLedger> ledgerEntries = new ArrayList<>();

        for (AllocationPlan.AllocationLine line : plan.lines()) {
            Warehouse warehouse = ensureWarehouse(line.warehouse());
            Integer warehouseId = warehouse.getId();
            Integer productId = line.product().getId();

            WarehouseStock stock = warehouseStockRepo
                    .lockRow(warehouseId, productId)
                    .orElseGet(() -> {
                        WarehouseStock newStock = new WarehouseStock();
                        newStock.setId(new WarehouseStockId(warehouseId, productId));
                        newStock.setWarehouse(warehouse);
                        newStock.setProduct(line.product());
                        newStock.setQtyOnHand(0);
                        newStock.setQtyReserved(0);
                        return newStock;
                    });

            Integer onHand = Objects.requireNonNullElse(stock.getQtyOnHand(), 0);
            Integer reserved = Objects.requireNonNullElse(stock.getQtyReserved(), 0);
            if (onHand - reserved < line.quantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient available stock when reserving product " + line.product().getSku());
            }

            stock.setQtyReserved(reserved + line.quantity());
            warehouseStockRepo.save(stock);

            Fulfillment fulfillment = fulfillmentsByWarehouse.computeIfAbsent(warehouseId, id -> {
                Fulfillment f = new Fulfillment();
                f.setOrder(order);
                order.getFulfillments().add(f);
                f.setWarehouse(warehouse);
                f.setStatus("ALLOCATED");
                f.setAllocatedAt(now);
                return f;
            });

            FulfillmentItem fulfillmentItem = new FulfillmentItem();
            fulfillmentItem.setFulfillment(fulfillment);
            fulfillmentItem.setProduct(line.product());
            fulfillmentItem.setQuantityPicked(line.quantity());
            fulfillment.getItems().add(fulfillmentItem);
            fulfillmentItems.add(fulfillmentItem);

            StockLedger ledger = new StockLedger();
            ledger.setWarehouse(warehouse);
            ledger.setProduct(line.product());
            ledger.setOrder(order);
            ledger.setReason("ALLOCATE");
            ledger.setQuantityDelta(-line.quantity());
            ledger.setCreatedAt(now);
            ledgerEntries.add(ledger);
        }

        if (!fulfillmentsByWarehouse.isEmpty()) {
            fulfillmentRepo.saveAll(fulfillmentsByWarehouse.values());
            for (FulfillmentItem item : fulfillmentItems) {
                FulfillmentItemId id = item.getId();
                id.setFulfillmentId(item.getFulfillment().getId());
                id.setProductId(item.getProduct().getId());
            }
            fulfillmentItemRepo.saveAll(fulfillmentItems);
        }
        if (!ledgerEntries.isEmpty()) {
            stockLedgerRepo.saveAll(ledgerEntries);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseStock(CustomerOrder order) {
        if (order.getFulfillments().isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Fulfillment> fulfillments = new ArrayList<>(order.getFulfillments());
        List<StockLedger> ledgerEntries = new ArrayList<>();

        for (Fulfillment fulfillment : fulfillments) {
            Warehouse warehouse = ensureWarehouse(fulfillment.getWarehouse());

            for (FulfillmentItem item : new ArrayList<>(fulfillment.getItems())) {
                Integer productId = item.getProduct().getId();
                Integer quantity = Objects.requireNonNullElse(item.getQuantityPicked(), 0);
                if (quantity <= 0) {
                    continue;
                }

                WarehouseStock stock = warehouseStockRepo
                        .lockRow(warehouse.getId(), productId)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Unable to locate warehouse stock during release."));

                Integer reserved = Objects.requireNonNullElse(stock.getQtyReserved(), 0);
                stock.setQtyReserved(Math.max(0, reserved - quantity));
                warehouseStockRepo.save(stock);

                StockLedger ledger = new StockLedger();
                ledger.setWarehouse(warehouse);
                ledger.setProduct(item.getProduct());
                ledger.setOrder(order);
                ledger.setReason("CANCEL");
                ledger.setQuantityDelta(quantity);
                ledger.setCreatedAt(now);
                ledgerEntries.add(ledger);
            }
        }

        if (!ledgerEntries.isEmpty()) {
            stockLedgerRepo.saveAll(ledgerEntries);
        }

        fulfillmentRepo.deleteAll(fulfillments);
        order.getFulfillments().clear();
    }

    private Warehouse ensureWarehouse(Warehouse warehouse) {
        if (warehouse == null || warehouse.getId() == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Allocation missing warehouse.");
        }
        return warehouse;
    }
}
