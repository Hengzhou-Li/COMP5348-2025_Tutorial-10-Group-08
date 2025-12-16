package com.example.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FulfillmentItemId implements Serializable {

    @Column(name = "fulfillment_id")
    private Integer fulfillmentId;

    @Column(name = "product_id")
    private Integer productId;

    public FulfillmentItemId() {
    }

    public FulfillmentItemId(Integer fulfillmentId, Integer productId) {
        this.fulfillmentId = fulfillmentId;
        this.productId = productId;
    }

    public Integer getFulfillmentId() {
        return fulfillmentId;
    }

    public void setFulfillmentId(Integer fulfillmentId) {
        this.fulfillmentId = fulfillmentId;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FulfillmentItemId that = (FulfillmentItemId) o;
        return Objects.equals(fulfillmentId, that.fulfillmentId)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fulfillmentId, productId);
    }
}
