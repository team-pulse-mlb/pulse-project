package com.pulse.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchScoreRepository extends JpaRepository<WatchScore, Long> {

    Optional<WatchScore> findTopByGameIdOrderByComputedAtDesc(Long gameId);

    @Query("""
            select watchScore from WatchScore watchScore
            where watchScore.gameId in :gameIds
              and not exists (
                  select newer.id from WatchScore newer
                  where newer.gameId = watchScore.gameId
                    and (newer.computedAt > watchScore.computedAt
                      or (newer.computedAt = watchScore.computedAt and newer.id > watchScore.id))
              )
            """)
    List<WatchScore> findLatestByGameIdIn(@Param("gameIds") Collection<Long> gameIds);

    boolean existsByGameIdAndComputedAt(Long gameId, Instant computedAt);

    List<WatchScore> findByGameIdOrderByComputedAtAsc(Long gameId);

    /** 급등 판정용. 창(window) 안의 최저 watch_score를 반환한다. */
    @Query("select min(w.watchScore) from WatchScore w "
            + "where w.gameId = :gameId and w.computedAt >= :since")
    Integer findMinWatchScoreSince(@Param("gameId") Long gameId, @Param("since") Instant since);
}
