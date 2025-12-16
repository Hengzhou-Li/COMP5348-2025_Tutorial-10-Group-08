package com.example.deliveryco_service.repository;

import com.example.deliveryco_service.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepo extends JpaRepository<OutboxEvent, Integer> {

    /**
     * Find all outbox events that are ready to be published
     * (created_at <= current time - since we don't have publish_at column)
     */
    @Query("SELECT e FROM OutboxEvent e ORDER BY e.createdAt ASC")
    List<OutboxEvent> findReadyToPublish();

    /**
     * Find outbox events by aggregate type, ordered by creation time
     */
    List<OutboxEvent> findByAggregateTypeOrderByCreatedAtAsc(String aggregateType);

    /**
     * Find outbox events by aggregate type and event type
     */
    List<OutboxEvent> findByAggregateTypeAndEventType(String aggregateType, String eventType);

    /**
     * Find outbox events by aggregate ID
     */
    List<OutboxEvent> findByAggregateId(Integer aggregateId);
}
