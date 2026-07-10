package com.pulse.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    boolean existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
            Long gameId, String eventType, String sourceType, Long sourceRef);

    Optional<GameEvent> findFirstByGameIdAndSpoilerLevelOrderByObservedAtDesc(Long gameId, String spoilerLevel);
}
