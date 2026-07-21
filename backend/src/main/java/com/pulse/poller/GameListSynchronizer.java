package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import com.pulse.poller.PollerGameWriter.GameUpsertResult;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class GameListSynchronizer {

    private final BaseballDataSource balldontlieClient;
    private final GameRepository gameRepository;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final PollerLiveGameStateTracker stateTracker;
    private final LivePlaysPoller livePlaysPoller;
    private final GameTransitionWriter gameTransitionWriter;
    private final PollerBackoff gamesBackoff;

    private Instant nextGamesPollAt = Instant.EPOCH;

    GameListSynchronizer(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PollerLiveGameStateTracker stateTracker,
            LivePlaysPoller livePlaysPoller,
            GameTransitionWriter gameTransitionWriter
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.stateTracker = stateTracker;
        this.livePlaysPoller = livePlaysPoller;
        this.gameTransitionWriter = gameTransitionWriter;
        this.gamesBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    List<Game> syncGames(Instant now) {
        if (now.isBefore(nextGamesPollAt)) {
            return gameRepository.findByLifecycleState(GameLifecycle.LIVE.name());
        }
        if (!gamesBackoff.canCall(now)) {
            log.info("games poll skipped by backoff until {}", gamesBackoff.blockedUntil());
            return gameRepository.findByLifecycleState(GameLifecycle.LIVE.name());
        }

        Map<Long, Game> liveGames = new LinkedHashMap<>();
        int changedGames = 0;
        try {
            rateLimiter.acquire();
            for (BdlGame dto : balldontlieClient.getGames(slateDates(now))) {
                // 종료 status만 이전 상태를 조회하고 트랜잭션 밖에서 drain 데이터를 미리 가져온다.
                TerminalDrainData drain = null;
                if (isTerminalSourceStatus(dto.status())) {
                    Game pre = gameRepository.findById(dto.id()).orElse(null);
                    if (shouldDrain(pre)) {
                        Long cursor = pre == null ? null : pre.getLastPlayOrder();
                        drain = livePlaysPoller.fetchTerminalDrain(dto.id(), cursor, now);
                    }
                }
                // 상태 저장·drain 쓰기·전이 outbox 발행은 하나의 트랜잭션으로 처리한다.
                GameTransitionWriter.GameSyncOutcome outcome =
                        gameTransitionWriter.applyTransition(dto, drain, now);
                GameUpsertResult result = outcome.result();
                changedGames++;
                if (GameLifecycle.LIVE.name().equals(result.currentLifecycle())) {
                    liveGames.put(result.game().getId(), result.game());
                }
                if (result.enteredTerminalState()) {
                    stateTracker.clear(result.game().getId());
                }
            }
            gamesBackoff.recordSuccess();
            boolean hasLiveGame = !liveGames.isEmpty()
                    || !gameRepository.findByLifecycleState(GameLifecycle.LIVE.name()).isEmpty();
            nextGamesPollAt = now.plus(hasLiveGame ? properties.tickInterval() : gamesIntervalWithoutLiveGame(now));
            log.info("games poll completed: changedGames={}, liveGames={}", changedGames, liveGames.size());
        } catch (RuntimeException e) {
            handleFailure("games", gamesBackoff, now, e);
        }

        if (liveGames.isEmpty()) {
            return gameRepository.findByLifecycleState(GameLifecycle.LIVE.name());
        }
        return new ArrayList<>(liveGames.values());
    }

    private static boolean isTerminalSourceStatus(String status) {
        return status != null
                && (status.startsWith(Game.STATUS_FINAL)
                || Game.STATUS_CANCELED.equals(status)
                || Game.STATUS_POSTPONED.equals(status));
    }

    private static boolean shouldDrain(Game pre) {
        if (pre == null) {
            return true; // 신규 경기가 곧장 종료 status로 관측된 경우 1회 시도
        }
        String state = pre.getLifecycleState();
        return !GameLifecycle.FINAL.name().equals(state)
                && !GameLifecycle.DONE.name().equals(state)
                && !GameLifecycle.SUSPENDED_POSTPONED.name().equals(state);
    }

    private Duration gamesIntervalWithoutLiveGame(Instant now) {
        Duration threshold = properties.scheduledGamesNearThreshold();
        Instant nextStart = gameRepository.findNextScheduledStartTime(now.minus(threshold));
        if (nextStart == null) {
            return properties.idleGamesInterval();
        }
        Instant nearPollingStartsAt = nextStart.minus(threshold);
        if (!nearPollingStartsAt.isAfter(now)) {
            return properties.scheduledGamesInterval();
        }
        Duration untilNearPolling = Duration.between(now, nearPollingStartsAt);
        return untilNearPolling.compareTo(properties.idleGamesInterval()) < 0
                ? untilNearPolling
                : properties.idleGamesInterval();
    }

    private List<LocalDate> slateDates(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<LocalDate> dates = new ArrayList<>();
        for (int offset = -properties.slateLookbackDays(); offset <= properties.slateLookaheadDays(); offset++) {
            dates.add(today.plusDays(offset));
        }
        return dates;
    }

    private void handleFailure(String target, PollerBackoff backoff, Instant now, RuntimeException e) {
        if (PollerExceptionClassifier.shouldBackoff(e)) {
            backoff.recordFailure(now, PollerExceptionClassifier.retryAfter(e));
            PulseMetrics.increment("pulse.poller.backoff.activations", "target", target);
            log.warn("{} poll failed, backed off until {}", target, backoff.blockedUntil(), e);
            return;
        }
        throw e;
    }
}
