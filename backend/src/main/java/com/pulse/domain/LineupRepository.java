package com.pulse.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineupRepository extends JpaRepository<Lineup, Long> {

    List<Lineup> findByGameId(Long gameId);

    Optional<Lineup> findByGameIdAndPlayerId(Long gameId, Long playerId);

    List<Lineup> findByGameIdAndIsProbablePitcherTrue(Long gameId);

    List<Lineup> findByGameIdInAndIsProbablePitcherTrue(List<Long> gameIds);
}
