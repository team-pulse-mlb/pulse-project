package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import com.pulse.poller.PregameTransitionWriter.PregameWriteOutcome;
import com.pulse.poller.PregameTransitionWriter.PregameWriteRequest;
import com.pulse.poller.PregameTransitionWriter.StandingsBatch;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경기 전 데이터 수집기. PREGAME_FAR/NEAR 라인업, PREGAME_NEAR 배당,
 * 순위 스냅샷을 수집하고 입력 갱신 시 PREGAME task를 발행한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class PregamePoller {

    private final BaseballDataSource balldontlieClient;
    private final GameRepository gameRepository;
    private final PregameTransitionWriter transitionWriter;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final Clock clock;
    private final PollerBackoff backoff;

    private final Map<Long, Instant> nextLineupsPollAt = new HashMap<>();
    private final Set<Long> pregameNearTaskPublished = new HashSet<>();
    private Instant nextOddsPollAt = Instant.EPOCH;
    private LocalDate lastStandingsBatchDate;

    @Autowired
    public PregamePoller(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PregameTransitionWriter transitionWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter
    ) {
        this(
                balldontlieClient,
                gameRepository,
                transitionWriter,
                properties,
                rateLimiter,
                Clock.systemUTC()
        );
    }

    PregamePoller(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PregameTransitionWriter transitionWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            Clock clock
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.transitionWriter = transitionWriter;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.backoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    @Scheduled(fixedDelayString = "${pulse.poller.pregame-tick-delay-ms:60000}")
    public void poll() {
        PulseMetrics.increment("pulse.poller.ticks", "poller", "pregame");
        Instant now = clock.instant();
        if (!backoff.canCall(now)) {
            return;
        }

        List<Game> pregameGames = gameRepository.findByLifecycleStateIn(List.of(
                GameLifecycle.PREGAME_FAR.name(),
                GameLifecycle.PREGAME_NEAR.name()
        ));
        Map<Long, Game> gamesById = pregameGames.stream()
                .collect(Collectors.toMap(Game::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        prunePollingState(gamesById.keySet(), pregameGames);

        Set<Long> triggeredGameIds = new LinkedHashSet<>();
        LineupsPollResult lineupsResult = LineupsPollResult.notPolled();
        OddsPollResult oddsResult = OddsPollResult.notPolled();
        StandingsBatch standingsBatch = null;
        SeasonStatsFetchResult seasonStatsResult = SeasonStatsFetchResult.empty();
        try {
            triggeredGameIds.addAll(detectPregameNearEntries(pregameGames));
            lineupsResult = pollLineups(pregameGames, now);
            seasonStatsResult = loadProbablePitcherSeasonStats(lineupsResult.lineups(), now);
            oddsResult = pollOdds(pregameGames, now);
            standingsBatch = runStandingsDailyBatch(now);
            backoff.recordSuccess();
        } catch (RuntimeException e) {
            if (!PollerExceptionClassifier.shouldBackoff(e)) {
                throw e;
            }
            backoff.recordFailure(now, PollerExceptionClassifier.retryAfter(e));
            PulseMetrics.increment("pulse.poller.backoff.activations", "target", "pregame");
            log.warn("pregame poll failed, backed off until {}", backoff.blockedUntil(), e);
        }

        PregameWriteOutcome outcome = transitionWriter.write(new PregameWriteRequest(
                lineupsResult.lineups(),
                oddsResult.odds(),
                oddsResult.startTimesByGameId(),
                standingsBatch,
                LocalDate.ofInstant(now, ZoneOffset.UTC).getYear(),
                seasonStatsResult.stats(),
                seasonStatsResult.pitcherIdsByGameId(),
                triggeredGameIds,
                gamesById,
                now
        ));
        updatePollingStateAfterCommit(lineupsResult, oddsResult, standingsBatch, outcome, gamesById, now);
    }

    /** PREGAME_NEAR 신규 진입 경기를 한 번만 트리거한다. */
    private Set<Long> detectPregameNearEntries(List<Game> pregameGames) {
        Set<Long> entered = new LinkedHashSet<>();
        for (Game game : pregameGames) {
            if (GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState())
                    && !pregameNearTaskPublished.contains(game.getId())) {
                entered.add(game.getId());
            }
        }
        return entered;
    }

    private LineupsPollResult pollLineups(List<Game> pregameGames, Instant now) {
        List<Game> dueGames = pregameGames.stream()
                .filter(game -> !now.isBefore(nextLineupsPollAt.getOrDefault(game.getId(), Instant.EPOCH)))
                .toList();
        if (dueGames.isEmpty()) {
            return LineupsPollResult.notPolled();
        }

        rateLimiter.acquire();
        List<BdlLineup> lineups = balldontlieClient.getLineups(dueGames.stream().map(Game::getId).toList());
        return new LineupsPollResult(lineups, dueGames);
    }

    private OddsPollResult pollOdds(List<Game> pregameGames, Instant now) {
        List<Game> nearGames = pregameGames.stream()
                .filter(game -> GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState()))
                .toList();
        if (nearGames.isEmpty() || now.isBefore(nextOddsPollAt)) {
            return OddsPollResult.notPolled();
        }

        rateLimiter.acquire();
        List<BdlOdds> odds = balldontlieClient.getOdds(nearGames.stream().map(Game::getId).toList());
        Map<Long, Instant> startTimes = new HashMap<>();
        for (Game game : pregameGames) {
            if (game.getStartTime() != null) {
                startTimes.put(game.getId(), game.getStartTime());
            }
        }
        return new OddsPollResult(odds, startTimes, true);
    }

    /** 순위 일 배치. 반영 시 경기 전 상태의 모든 경기를 재계산 대상으로 본다. */
    private StandingsBatch runStandingsDailyBatch(Instant now) {
        ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC);
        LocalDate today = utcNow.toLocalDate();
        if (utcNow.getHour() < properties.standingsBatchHourUtc() || today.equals(lastStandingsBatchDate)) {
            return null;
        }

        rateLimiter.acquire();
        int season = utcNow.getYear();
        List<BdlStanding> standings = balldontlieClient.getStandings(season);
        return new StandingsBatch(season, today, standings);
    }

    /** 조회한 라인업 DTO에서 선발 투수 id를 추출해 트랜잭션 밖에서 시즌 스탯을 가져온다. */
    private SeasonStatsFetchResult loadProbablePitcherSeasonStats(List<BdlLineup> lineups, Instant now) {
        Map<Long, Set<Long>> pitcherIdsByGameId = lineups.stream()
                .filter(lineup -> Boolean.TRUE.equals(lineup.isProbablePitcher()))
                .filter(lineup -> lineup.gameId() != null
                        && lineup.player() != null
                        && lineup.player().id() != null)
                .collect(Collectors.groupingBy(
                        BdlLineup::gameId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                lineup -> lineup.player().id(),
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ));
        List<Long> pitcherIds = pitcherIdsByGameId.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .toList();
        if (pitcherIds.isEmpty()) {
            return SeasonStatsFetchResult.empty();
        }
        int season = LocalDate.ofInstant(now, ZoneOffset.UTC).getYear();
        try {
            rateLimiter.acquire();
            return new SeasonStatsFetchResult(
                    balldontlieClient.getPlayerSeasonStats(season, pitcherIds),
                    pitcherIdsByGameId
            );
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            log.warn("season stats refresh failed, falling back to cached values: pitcherIds={}", pitcherIds, e);
            return new SeasonStatsFetchResult(List.of(), pitcherIdsByGameId);
        }
    }

    private void updatePollingStateAfterCommit(
            LineupsPollResult lineupsResult,
            OddsPollResult oddsResult,
            StandingsBatch standingsBatch,
            PregameWriteOutcome outcome,
            Map<Long, Game> gamesById,
            Instant now
    ) {
        for (Game game : lineupsResult.dueGames()) {
            boolean near = GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState());
            nextLineupsPollAt.put(
                    game.getId(),
                    now.plus(near ? properties.lineupsNearInterval() : properties.lineupsFarInterval())
            );
        }
        if (oddsResult.polled()) {
            nextOddsPollAt = now.plus(properties.oddsInterval());
        }
        if (standingsBatch != null) {
            lastStandingsBatchDate = standingsBatch.snapshotDate();
            log.info("standings daily batch completed: season={}, snapshotDate={}",
                    standingsBatch.season(), standingsBatch.snapshotDate());
        }
        for (Long gameId : outcome.publishedGameIds()) {
            Game game = gamesById.get(gameId);
            if (game != null && GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState())) {
                pregameNearTaskPublished.add(gameId);
            }
        }
        if (!outcome.changedGameIds().isEmpty()) {
            log.info("pregame inputs committed and tasks published: games={}", outcome.changedGameIds().size());
        }
    }

    private void prunePollingState(Set<Long> currentGameIds, List<Game> pregameGames) {
        nextLineupsPollAt.keySet().retainAll(currentGameIds);
        Set<Long> nearGameIds = pregameGames.stream()
                .filter(game -> GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState()))
                .map(Game::getId)
                .collect(Collectors.toSet());
        pregameNearTaskPublished.retainAll(nearGameIds);
    }

    private record LineupsPollResult(List<BdlLineup> lineups, List<Game> dueGames) {

        private static LineupsPollResult notPolled() {
            return new LineupsPollResult(List.of(), List.of());
        }
    }

    private record OddsPollResult(List<BdlOdds> odds, Map<Long, Instant> startTimesByGameId, boolean polled) {

        private static OddsPollResult notPolled() {
            return new OddsPollResult(List.of(), Map.of(), false);
        }
    }

    private record SeasonStatsFetchResult(
            List<BdlPlayerSeasonStat> stats,
            Map<Long, Set<Long>> pitcherIdsByGameId
    ) {

        private static SeasonStatsFetchResult empty() {
            return new SeasonStatsFetchResult(List.of(), Map.of());
        }
    }
}
