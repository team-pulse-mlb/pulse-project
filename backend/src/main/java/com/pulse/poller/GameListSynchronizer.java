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
    private final PollerGameWriter gameWriter;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final PollerLiveGameStateTracker stateTracker;
    private final LivePlaysPoller livePlaysPoller;
    private final GameTransitionEventNotifier transitionEventNotifier;
    private final PollerBackoff gamesBackoff;

    private Instant nextGamesPollAt = Instant.EPOCH;

    GameListSynchronizer(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PollerGameWriter gameWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PollerLiveGameStateTracker stateTracker,
            LivePlaysPoller livePlaysPoller,
            GameTransitionEventNotifier transitionEventNotifier
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.gameWriter = gameWriter;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.stateTracker = stateTracker;
        this.livePlaysPoller = livePlaysPoller;
        this.transitionEventNotifier = transitionEventNotifier;
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
                GameUpsertResult result = gameWriter.upsertGame(dto, now);
                changedGames++;
                if (GameLifecycle.LIVE.name().equals(result.currentLifecycle())) {
                    liveGames.put(result.game().getId(), result.game());
                }
                if (result.enteredTerminalState()) {
                    stateTracker.clear(result.game().getId());
                }
                boolean terminalScoreTaskPublished = result.enteredTerminalState()
                        && livePlaysPoller.drainTerminalGame(result.game(), now);
                transitionEventNotifier.publishTransitionEvents(
                        result,
                        GameTransitionEventNotifier.terminalTaskObservedAt(now, terminalScoreTaskPublished)
                );
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
