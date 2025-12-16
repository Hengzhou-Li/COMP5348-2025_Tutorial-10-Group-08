package com.example.store.repository;

import com.example.store.model.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepo extends JpaRepository<OutboxEvent, Integer> {

    List<OutboxEvent> findByPublishAtIsNullOrPublishAtLessThanEqual(LocalDateTime publishAt);

    Optional<OutboxEvent> findFirstByAggregateTypeAndAggregateIdAndEventTypeOrderByCreatedAtDesc(
            String aggregateType, Integer aggregateId, String eventType);
}
