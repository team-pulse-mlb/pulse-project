package com.pulse.poller;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경기 전 저빈도 수집기. PREGAME_FAR/NEAR 라인업, PREGAME_NEAR 배당,
 * 순위 일 배치를 수집하고 입력 갱신 시 PREGAME task를 발행한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class PregamePoller {

    private final BalldontlieClient balldontlieClient;
    private final GameRepository gameRepository;
    private final LineupRepository lineupRepository;
    private final PregameGameWriter pregameWriter;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final Clock clock;
    private final PollerBackoff backoff;

    private final Map<Long, Instant> nextLineupsPollAt = new HashMap<>();
    private final Set<Long> pregameNearTaskPublished = new HashSet<>();
    private Instant nextOddsPollAt = Instant.EPOCH;
    private LocalDate lastStandingsBatchDate;

    public PregamePoller(
            BalldontlieClient balldontlieClient,
            GameRepository gameRepository,
            LineupRepository lineupRepository,
            PregameGameWriter pregameWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter
    ) {
        this(
                balldontlieClient,
                gameRepository,
                lineupRepository,
                pregameWriter,
                scoreTaskFactory,
                scoreTaskPublisher,
                properties,
                rateLimiter,
                Clock.systemUTC()
        );
    }

    PregamePoller(
            BalldontlieClient balldontlieClient,
            GameRepository gameRepository,
            LineupRepository lineupRepository,
            PregameGameWriter pregameWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            Clock clock
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.lineupRepository = lineupRepository;
        this.pregameWriter = pregameWriter;
        this.scoreTaskFactory = scoreTaskFactory;
        this.scoreTaskPublisher = scoreTaskPublisher;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.backoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    @Scheduled(fixedDelayString = "${pulse.poller.pregame-tick-delay-ms:60000}")
    public void poll() {
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

        Set<Long> changedGameIds = new LinkedHashSet<>();
        try {
            changedGameIds.addAll(detectPregameNearEntries(pregameGames));
            changedGameIds.addAll(pollLineups(pregameGames, now));
            changedGameIds.addAll(pollOdds(pregameGames, now));
            changedGameIds.addAll(runStandingsDailyBatch(pregameGames, now));
            backoff.recordSuccess();
        } catch (RuntimeException e) {
            if (!PollerExceptionClassifier.shouldBackoff(e)) {
                throw e;
            }
            backoff.recordFailure(now, PollerExceptionClassifier.retryAfter(e));
            log.warn("pregame poll failed, backed off until {}", backoff.blockedUntil(), e);
        }

        publishPregameTasks(changedGameIds, gamesById, now);
    }

    /** PREGAME_NEAR 신규 진입 경기를 1회 트리거한다. */
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

    private Set<Long> pollLineups(List<Game> pregameGames, Instant now) {
        List<Game> dueGames = pregameGames.stream()
                .filter(game -> !now.isBefore(nextLineupsPollAt.getOrDefault(game.getId(), Instant.EPOCH)))
                .toList();
        if (dueGames.isEmpty()) {
            return Set.of();
        }

        rateLimiter.acquire();
        List<BdlLineup> lineups = balldontlieClient.getLineups(dueGames.stream().map(Game::getId).toList());
        Set<Long> changedGameIds = pregameWriter.upsertLineups(lineups, now);
        for (Game game : dueGames) {
            boolean near = GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState());
            nextLineupsPollAt.put(
                    game.getId(),
                    now.plus(near ? properties.lineupsNearInterval() : properties.lineupsFarInterval())
            );
        }
        if (!changedGameIds.isEmpty()) {
            log.info("lineups poll completed: dueGames={}, changedGames={}", dueGames.size(), changedGameIds.size());
        }
        return changedGameIds;
    }

    private Set<Long> pollOdds(List<Game> pregameGames, Instant now) {
        List<Game> nearGames = pregameGames.stream()
                .filter(game -> GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState()))
                .toList();
        if (nearGames.isEmpty() || now.isBefore(nextOddsPollAt)) {
            return Set.of();
        }

        rateLimiter.acquire();
        List<BdlOdds> odds = balldontlieClient.getOdds(LocalDate.ofInstant(now, ZoneOffset.UTC));
        Map<Long, Instant> startTimes = new HashMap<>();
        for (Game game : pregameGames) {
            if (game.getStartTime() != null) {
                startTimes.put(game.getId(), game.getStartTime());
            }
        }
        Set<Long> changedGameIds = pregameWriter.upsertOdds(odds, startTimes, now);
        nextOddsPollAt = now.plus(properties.oddsInterval());
        if (!changedGameIds.isEmpty()) {
            log.info("odds poll completed: changedGames={}", changedGameIds.size());
        }
        return changedGameIds;
    }

    /** 순위 일 배치. 반영 시 경기 전 상태의 모든 경기를 재계산 대상으로 본다. */
    private Set<Long> runStandingsDailyBatch(List<Game> pregameGames, Instant now) {
        ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC);
        LocalDate today = utcNow.toLocalDate();
        if (utcNow.getHour() < properties.standingsBatchHourUtc() || today.equals(lastStandingsBatchDate)) {
            return Set.of();
        }

        rateLimiter.acquire();
        int season = utcNow.getYear();
        List<BdlStanding> standings = balldontlieClient.getStandings(season);
        boolean inserted = pregameWriter.upsertStandings(season, today, standings, now);
        lastStandingsBatchDate = today;
        log.info("standings daily batch completed: season={}, snapshotDate={}, inserted={}", season, today, inserted);
        if (!inserted) {
            return Set.of();
        }
        return pregameGames.stream().map(Game::getId).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void publishPregameTasks(Set<Long> changedGameIds, Map<Long, Game> gamesById, Instant now) {
        for (Long gameId : changedGameIds) {
            Game game = gamesById.get(gameId);
            if (game == null) {
                continue;
            }
            refreshProbablePitcherSeasonStats(game, now);
            scoreTaskPublisher.publish(scoreTaskFactory.pregameTask(game, now));
            if (GameLifecycle.PREGAME_NEAR.name().equals(game.getLifecycleState())) {
                pregameNearTaskPublished.add(gameId);
            }
        }
        if (!changedGameIds.isEmpty()) {
            log.info("pregame tasks published: games={}", changedGameIds.size());
        }
    }

    /** task 발행 전 확정 선발의 시즌 스탯을 온디맨드 적재한다. 실패 시 직전 캐시를 사용한다. */
    private void refreshProbablePitcherSeasonStats(Game game, Instant now) {
        List<Long> pitcherIds = lineupRepository.findByGameIdAndIsProbablePitcherTrue(game.getId()).stream()
                .map(Lineup::getPlayerId)
                .distinct()
                .toList();
        if (pitcherIds.isEmpty()) {
            return;
        }
        int season = LocalDate.ofInstant(now, ZoneOffset.UTC).getYear();
        try {
            rateLimiter.acquire();
            List<BdlPlayerSeasonStat> stats = balldontlieClient.getPlayerSeasonStats(season, pitcherIds);
            pregameWriter.upsertPlayerSeasonStats(season, stats, now);
        } catch (RuntimeException e) {
            log.warn("season stats refresh failed, falling back to cached values: gameId={}", game.getId(), e);
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
}
