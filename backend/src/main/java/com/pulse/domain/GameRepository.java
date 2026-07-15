package com.pulse.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByStartTimeGreaterThanEqualAndStartTimeLessThan(Instant startInclusive, Instant endExclusive);

    List<Game> findByStatusAndStartTimeBetween(String status, Instant start, Instant end);

    List<Game> findByStatusStartingWithAndStartTimeGreaterThanEqual(String statusPrefix, Instant start);

    /** 창 밖 보충용: 상태 접두사에 해당하는 경기를 정렬·페이지 기준으로 조회한다. */
    List<Game> findByStatusStartingWith(String statusPrefix, Pageable pageable);

    /** 창 밖 보충용: 지정 시각 이후 시작하는 특정 상태 경기를 정렬·페이지 기준으로 조회한다. */
    List<Game> findByStatusAndStartTimeGreaterThanEqual(String status, Instant start, Pageable pageable);

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
