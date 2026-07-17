package com.pulse.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByStatus(String status);

    List<Game> findByStartTimeGreaterThanEqualAndStartTimeLessThan(Instant startInclusive, Instant endExclusive);

    List<Game> findByStatusAndStartTimeBetween(String status, Instant start, Instant end);

    List<Game> findByStatusStartingWithAndStartTimeGreaterThanEqual(String statusPrefix, Instant start);

    List<Game> findByStatusAndStartTimeGreaterThanEqual(String status, Instant start);

    /** 창 밖 보충용: 상태 접두사에 해당하는 경기를 정렬·페이지 기준으로 조회한다. */
    List<Game> findByStatusStartingWith(String statusPrefix, Pageable pageable);

    /** 창 밖 보충용: 지정 시각 이후 시작하는 특정 상태 경기를 정렬·페이지 기준으로 조회한다. */
    List<Game> findByStatusAndStartTimeGreaterThanEqual(String status, Instant start, Pageable pageable);

    List<Game> findByLifecycleState(String lifecycleState);

    List<Game> findByLifecycleStateIn(Collection<String> lifecycleStates);

    /** 종료 task 복구용: 최근에 갱신된 terminal 경기만 스캔한다. */
    List<Game> findByLifecycleStateInAndUpdatedAtAfter(Collection<String> lifecycleStates, Instant updatedAfter);

    /** FINAL 후처리 권한을 DB에서 원자적으로 한 번만 획득한다. */
    @Modifying
    @Query("update Game game set game.finalizedAt = :processedAt "
            + "where game.id = :gameId and game.finalizedAt is null")
    int markFinalized(@Param("gameId") Long gameId, @Param("processedAt") Instant processedAt);

    /** DONE terminal task 처리 기록을 DB에서 원자적으로 남긴다. */
    @Modifying
    @Query("update Game game set game.terminalDoneAt = :processedAt "
            + "where game.id = :gameId and game.terminalDoneAt is null")
    int markDone(@Param("gameId") Long gameId, @Param("processedAt") Instant processedAt);

    /** SUSPENDED_POSTPONED terminal task 처리 기록을 DB에서 원자적으로 남긴다. */
    @Modifying
    @Query("update Game game set game.terminalSuspendedPostponedAt = :processedAt "
            + "where game.id = :gameId and game.terminalSuspendedPostponedAt is null")
    int markSuspendedPostponed(
            @Param("gameId") Long gameId,
            @Param("processedAt") Instant processedAt
    );

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

    @Query("""
            select game.id
            from Game game
            where game.status like 'STATUS_FINAL%'
            order by game.startTime, game.id
            """)
    List<Long> findAllFinalGameIds();

    @Query("""
            select game.id
            from Game game
            where game.status like 'STATUS_FINAL%'
              and game.startTime >= :startInclusive
              and game.startTime < :endExclusive
            order by game.startTime, game.id
            """)
    List<Long> findFinalGameIdsByStartTimeBetween(Instant startInclusive, Instant endExclusive);
}
