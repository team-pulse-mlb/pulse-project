package com.pulse.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScoreTaskOutboxRepository extends JpaRepository<ScoreTaskOutbox, UUID> {

    Optional<ScoreTaskOutbox> findByGameIdAndObservedAt(Long gameId, Instant observedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox FROM ScoreTaskOutbox outbox
            WHERE outbox.outboxId = :outboxId AND outbox.status = 'PENDING'
            """)
    Optional<ScoreTaskOutbox> findPendingByOutboxIdForUpdate(@Param("outboxId") UUID outboxId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox FROM ScoreTaskOutbox outbox
            WHERE outbox.status = 'PENDING' AND outbox.nextAttemptAt <= :now
            ORDER BY outbox.createdAt
            """)
    List<ScoreTaskOutbox> findReadyForUpdate(@Param("now") Instant now, Pageable pageable);
}
