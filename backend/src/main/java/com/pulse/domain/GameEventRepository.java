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

    /**
     * 급변 하이라이트의 anchor 후보를 찾는다.
     * 윈도 안에서 아직 하이라이트로 표시되지 않은 가장 최근 보호 이벤트를 고른다.
     */
    Optional<GameEvent>
            findFirstByGameIdAndSpoilerLevelAndTimelineHighlightFalseAndObservedAtGreaterThanEqualOrderByObservedAtDescIdDesc(
                    Long gameId, String spoilerLevel, Instant since);

    /**
     * 하이라이트 쿨다운 판정용. 최근 윈도에 이미 표시된 하이라이트가 있으면 true.
     */
    boolean existsByGameIdAndTimelineHighlightTrueAndObservedAtGreaterThanEqual(
            Long gameId, Instant since);

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
