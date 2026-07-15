package com.pulse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    boolean existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
            Long gameId, String eventType, String sourceType, Long sourceRef);

    long countByGameIdAndEventType(Long gameId, String eventType);

    long countBySpoilerLevel(String spoilerLevel);

    Optional<GameEvent> findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
            Long gameId, String spoilerLevel);

    List<GameEvent> findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(Long gameId, String spoilerLevel);

    @Query("""
            SELECT gameEvent FROM GameEvent gameEvent
            WHERE gameEvent.spoilerLevel = 'PROTECTED_SAFE'
              AND gameEvent.copyProtected IS NULL
              AND gameEvent.copyProtectedAttempts < :maxAttempts
              AND gameEvent.observedAt >= :since
            ORDER BY gameEvent.observedAt ASC
            """)
    List<GameEvent> findProtectedCopyRetryTargets(
            @Param("maxAttempts") int maxAttempts,
            @Param("since") Instant since,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(MAX(gameEvent.id), 0)
            FROM GameEvent gameEvent
            WHERE gameEvent.spoilerLevel = 'PROTECTED_SAFE'
            """)
    long findMaxProtectedEventId();

    @Query("""
            SELECT gameEvent FROM GameEvent gameEvent
            WHERE gameEvent.spoilerLevel = 'PROTECTED_SAFE'
              AND gameEvent.id > :afterId
              AND gameEvent.id <= :maxId
            ORDER BY gameEvent.id ASC
            """)
    List<GameEvent> findProtectedAiReprocessTargets(
            @Param("afterId") long afterId,
            @Param("maxId") long maxId,
            Pageable pageable);

}
