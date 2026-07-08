package com.pulse.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    boolean existsByGameIdAndEventTypeAndSourceTypeAndSourceRef(
            Long gameId, String eventType, String sourceType, Long sourceRef);
}
