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
@Table(name = "fulfillment_item")
public class FulfillmentItem {

    @EmbeddedId
    private FulfillmentItemId id = new FulfillmentItemId();

    @MapsId("fulfillmentId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfillment_id", nullable = false)
    private Fulfillment fulfillment;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "qty_picked")
    private Integer quantityPicked;

    public FulfillmentItemId getId() {
        return id;
    }

    public void setId(FulfillmentItemId id) {
        this.id = id;
    }

    public Fulfillment getFulfillment() {
        return fulfillment;
    }

    public void setFulfillment(Fulfillment fulfillment) {
        this.fulfillment = fulfillment;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantityPicked() {
        return quantityPicked;
    }

    public void setQuantityPicked(Integer quantityPicked) {
        this.quantityPicked = quantityPicked;
    }
}
