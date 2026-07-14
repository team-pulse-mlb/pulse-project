package com.pulse.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByStartTimeGreaterThanEqualAndStartTimeLessThan(Instant startInclusive, Instant endExclusive);

    List<Game> findByStatusAndStartTimeBetween(String status, Instant start, Instant end);

    List<Game> findByStatusStartingWithAndStartTimeGreaterThanEqual(String statusPrefix, Instant start);

    List<Game> findByLifecycleState(String lifecycleState);

    List<Game> findByLifecycleStateIn(Collection<String> lifecycleStates);

    @Query("""
            select game.id
            from Game game
            where game.status like 'STATUS_FINAL%'
              and (
                game.finalHeadlineProtected is null
                or trim(game.finalHeadlineProtected) = ''
                or game.finalHeadlineRevealed is null
                or trim(game.finalHeadlineRevealed) = ''
              )
            order by game.startTime, game.id
            """)
    List<Long> findFinalGameIdsMissingHeadlines();
}
