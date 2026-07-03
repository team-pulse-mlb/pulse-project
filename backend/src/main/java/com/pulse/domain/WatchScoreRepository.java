package com.pulse.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WatchScoreRepository extends JpaRepository<WatchScore, Long> {

    Optional<WatchScore> findTopByGameIdOrderByCreatedAtDesc(Long gameId);

    List<WatchScore> findTop10ByGameIdOrderByCreatedAtDesc(Long gameId);

}
