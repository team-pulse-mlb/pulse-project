package com.pulse.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    boolean existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
            Long gameId, String eventType, String sourceType, Long sourceRef);

    long countByGameIdAndEventType(Long gameId, String eventType);

    Optional<GameEvent> findFirstByGameIdAndSpoilerLevelOrderByObservedAtDescIdDesc(
            Long gameId, String spoilerLevel);

    List<GameEvent> findByGameIdAndSpoilerLevelOrderByObservedAtAscIdAsc(Long gameId, String spoilerLevel);
}
