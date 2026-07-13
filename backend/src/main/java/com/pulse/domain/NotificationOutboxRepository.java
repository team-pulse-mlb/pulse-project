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

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox FROM NotificationOutbox outbox
            WHERE outbox.eventId = :eventId AND outbox.status = 'PENDING'
            """)
    Optional<NotificationOutbox> findPendingByEventIdForUpdate(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox FROM NotificationOutbox outbox
            WHERE outbox.status = 'PENDING' AND outbox.nextAttemptAt <= :now
            ORDER BY outbox.createdAt
            """)
    List<NotificationOutbox> findReadyForUpdate(@Param("now") Instant now, Pageable pageable);
}
