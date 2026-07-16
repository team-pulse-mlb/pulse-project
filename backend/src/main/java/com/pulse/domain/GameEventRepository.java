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

    /** 공개 헤드라인 컨텍스트용 결과 이벤트를 한 번에 조회한다. */
    List<GameEvent> findByGameIdAndSpoilerLevelAndEventTypeInOrderByInningAscSourceRefAscIdAsc(
            Long gameId, String spoilerLevel, List<String> eventTypes);

    /**
     * 보호 모드 경기 흐름에 노출할 급변 하이라이트만 시간순으로 조회한다.
     *
     * 일반 보호 이벤트 전체를 반환하지 않고
     * is_timeline_highlight=true로 선택된 이벤트만 반환한다.
     */
    List<GameEvent>
            findByGameIdAndSpoilerLevelAndTimelineHighlightTrueOrderByObservedAtAscIdAsc(
                    Long gameId,
                    String spoilerLevel);

    /** 보호 문구의 '기여 라벨' 산출용. 같은 이닝의 보호 이벤트를 시간순으로 조회한다. */
    List<GameEvent> findByGameIdAndInningAndSpoilerLevelOrderByObservedAtAscIdAsc(
            Long gameId, Integer inning, String spoilerLevel);

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

    /** 기간 한정 재처리용: 대상 경기 목록이 작아 커서 배치 없이 한 번에 조회한다. */
    List<GameEvent> findBySpoilerLevelAndGameIdInOrderByGameIdAscObservedAtAsc(
            String spoilerLevel, List<Long> gameIds);

}
