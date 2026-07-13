package com.pulse.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchScoreRepository extends JpaRepository<WatchScore, Long> {

    Optional<WatchScore> findTopByGameIdOrderByComputedAtDesc(Long gameId);

    boolean existsByGameIdAndComputedAt(Long gameId, Instant computedAt);

    List<WatchScore> findByGameIdOrderByComputedAtAsc(Long gameId);

    /** 급등 판정용. 창(window) 안의 최저 watch_score를 반환한다. */
    @Query("select min(w.watchScore) from WatchScore w "
            + "where w.gameId = :gameId and w.computedAt >= :since")
    Integer findMinWatchScoreSince(@Param("gameId") Long gameId, @Param("since") Instant since);
}
