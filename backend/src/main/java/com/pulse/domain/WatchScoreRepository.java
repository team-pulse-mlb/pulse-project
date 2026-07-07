package com.pulse.domain;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchScoreRepository extends JpaRepository<WatchScore, Long> {

    Optional<WatchScore> findTopByGameIdOrderByComputedAtDesc(Long gameId);

    boolean existsByGameIdAndComputedAt(Long gameId, Instant computedAt);
}
