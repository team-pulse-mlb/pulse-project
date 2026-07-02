package com.pulse.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchScoreRepository extends JpaRepository<WatchScore, Long> {

    Optional<WatchScore> findTopByGameIdOrderByCreatedAtDesc(Long gameId);
}
