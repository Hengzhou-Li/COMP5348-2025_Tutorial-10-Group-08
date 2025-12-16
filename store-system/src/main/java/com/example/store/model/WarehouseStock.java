package com.example.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "warehouse_stock")
public class WarehouseStock {

    @EmbeddedId
    private WarehouseStockId id = new WarehouseStockId();

    @MapsId("warehouseId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "qty_on_hand")
    private Integer qtyOnHand = 0;

    @Column(name = "qty_reserved")
    private Integer qtyReserved = 0;

    public WarehouseStockId getId() {
        return id;
    }

    public void setId(WarehouseStockId id) {
        this.id = id;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = warehouse;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQtyOnHand() {
        return qtyOnHand;
    }

    public void setQtyOnHand(Integer qtyOnHand) {
        this.qtyOnHand = qtyOnHand;
    }

    public Integer getQtyReserved() {
        return qtyReserved;
    }

    public void setQtyReserved(Integer qtyReserved) {
        this.qtyReserved = qtyReserved;
    }
}
