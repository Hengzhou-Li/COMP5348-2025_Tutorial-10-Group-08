package com.example.deliveryco_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_event")
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Integer id;

    @Column(name = "delivery_id", nullable = false)
    private Integer deliveryId;

    @Column(name = "status")
    private String status;

    @Column(name = "details")
    private String details;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    // Constructors
    public DeliveryEvent() {}

    public DeliveryEvent(Integer deliveryId, String status, String details) {
        this.deliveryId = deliveryId;
        this.status = status;
        this.details = details;
        this.eventTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDeliveryId() {
        return deliveryId;
    }

    public void setDeliveryId(Integer deliveryId) {
        this.deliveryId = deliveryId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }
}

