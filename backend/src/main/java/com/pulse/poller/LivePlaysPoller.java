package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.client.BdlDtos.PlateAppearancesRaw;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.Game;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class LivePlaysPoller {

    private static final int LIVE_POLL_WORKERS = 6;

    private final BaseballDataSource balldontlieClient;
    private final PollerGameWriter gameWriter;
    private final LiveGameCycleWriter liveGameCycleWriter;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final PaRawArchiveUploader paRawArchiveUploader;
    private final PollerLiveGameStateTracker stateTracker;
    private final PollerBackoff playsBackoff;
    private final ExecutorService executor;

    LivePlaysPoller(
            BaseballDataSource balldontlieClient,
            PollerGameWriter gameWriter,
            LiveGameCycleWriter liveGameCycleWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PaRawArchiveUploader paRawArchiveUploader,
            PollerLiveGameStateTracker stateTracker
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameWriter = gameWriter;
        this.liveGameCycleWriter = liveGameCycleWriter;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.paRawArchiveUploader = paRawArchiveUploader;
        this.stateTracker = stateTracker;
        this.playsBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
        AtomicInteger workerSequence = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, "live-plays-worker-" + workerSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(LIVE_POLL_WORKERS, threadFactory);
    }

    void pollLiveGames(List<Game> liveGames, Instant now) {
        if (liveGames.isEmpty() || !playsBackoff.canCall(now)) {
            return;
        }

        AtomicBoolean backoffTriggered = new AtomicBoolean();
        List<Future<?>> futures = new ArrayList<>(liveGames.size());
        for (Game game : liveGames) {
            futures.add(executor.submit(() -> {
                if (backoffTriggered.get()) {
                    return;
                }
                pollLiveGame(game, now, backoffTriggered);
            }));
        }
        awaitCompletion(futures);
        if (!backoffTriggered.get()) {
            playsBackoff.recordSuccess();
        }
    }

    private void pollLiveGame(Game game, Instant now, AtomicBoolean backoffTriggered) {
        try {
            boolean quiet = stateTracker.quiet(game.getId(), now);
            if (quiet && stateTracker.quietPollScheduledAfter(game.getId(), now)) {
                PulseMetrics.increment("pulse.poller.quiet.skips");
                if (stateTracker.heartbeatDue(game.getId(), now)
                        && liveGameCycleWriter.publishHeartbeat(game, now)) {
                    stateTracker.markTaskPublished(game.getId(), now);
                }
                return;
            }
            if (quiet) {
                stateTracker.scheduleQuietPoll(game.getId(), now);
            }
            List<BdlPlay> plays = fetchPlays(game);
            if (plays.isEmpty() && game.getLastPlayOrder() != null) {
                int emptyFetchStreak = stateTracker.incrementEmptyFetchStreak(game.getId());
                if (emptyFetchStreak >= properties.cursorRecoveryEmptyTicks()) {
                    stateTracker.resetEmptyFetchStreak(game.getId());
                    plays = probeRecoveryCursor(game);
                }
            } else if (!plays.isEmpty()) {
                stateTracker.resetEmptyFetchStreak(game.getId());
                stateTracker.resetRecoveryStepBack(game.getId());
            }
            if (!plays.isEmpty()) {
                List<BdlPlateAppearance> plateAppearances = fetchPlateAppearances(game.getId(), now);
                LiveGameCycleWriter.CycleWriteResult result =
                        liveGameCycleWriter.write(game, plays, plateAppearances, now);
                logCycleResult(game.getId(), result);
                if (result.inserted() > 0) {
                    stateTracker.markNewPlays(game.getId(), now);
                    return;
                }
            }
            if (stateTracker.heartbeatDue(game.getId(), now)
                    && liveGameCycleWriter.publishHeartbeat(game, now)) {
                stateTracker.markTaskPublished(game.getId(), now);
            }
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            if (PollerExceptionClassifier.shouldBackoff(e)) {
                synchronized (playsBackoff) {
                    if (!backoffTriggered.get()) {
                        backoffTriggered.set(true);
                        handleFailure("plays", playsBackoff, now, e);
                    }
                }
                return;
            }
            log.error("plays poll failed: gameId={}", game.getId(), e);
            PulseMetrics.increment("pulse.poller.game.skips", "reason", "isolated_failure");
        }
    }

    private void awaitCompletion(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("plays poll interrupted", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("plays poll worker failed", cause);
            }
        }
    }

    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    boolean drainTerminalGame(Game game, Instant now) {
        try {
            List<BdlPlay> plays = fetchPlays(game);
            if (plays.isEmpty()) {
                return false;
            }
            List<BdlPlateAppearance> plateAppearances = fetchPlateAppearances(game.getId(), now);
            LiveGameCycleWriter.CycleWriteResult result =
                    liveGameCycleWriter.writeTerminalDrain(game, plays, plateAppearances, now);
            logCycleResult(game.getId(), result);
            return result.inserted() > 0;
        } catch (RuntimeException e) {
            if (PollerExceptionClassifier.shouldBackoff(e)) {
                handleFailure("plays", playsBackoff, now, e);
            } else {
                log.error("terminal plays drain failed: gameId={}", game.getId(), e);
                PulseMetrics.increment("pulse.poller.game.skips", "reason", "terminal_drain_failure");
            }
            return false;
        }
    }

    private List<BdlPlay> fetchPlays(Game game) {
        return fetchPlays(game.getId(), game.getLastPlayOrder());
    }

    private List<BdlPlay> probeRecoveryCursor(Game game) {
        int stepBack = stateTracker.incrementRecoveryStepBack(game.getId());
        Long probeCursor = gameWriter.findRecoveryCursor(game.getId(), stepBack);
        log.warn(
                "plays cursor recovery probe: gameId={}, currentCursor={}, probeCursor={}, stepBack={}",
                game.getId(),
                game.getLastPlayOrder(),
                probeCursor,
                stepBack
        );
        List<BdlPlay> plays = fetchPlays(game.getId(), probeCursor);
        String result = plays.isEmpty() ? "empty" : "data";
        PulseMetrics.increment("pulse.poller.cursor.recovery.probes", "result", result);
        if (!plays.isEmpty()) {
            stateTracker.resetRecoveryStepBack(game.getId());
        }
        return plays;
    }

    private List<BdlPlay> fetchPlays(long gameId, Long initialCursor) {
        Long cursor = initialCursor;
        List<BdlPlay> collected = new ArrayList<>();
        int pages = 0;

        while (pages < properties.maxPlayPagesPerGame()) {
            rateLimiter.acquire();
            ListResponse<BdlPlay> response = balldontlieClient.getPlays(gameId, cursor);
            List<BdlPlay> plays = response == null || response.data() == null ? List.of() : response.data();
            for (BdlPlay play : plays) {
                collected.add(play);
                if (play.order() != null) {
                    cursor = play.order();
                }
            }
            Long nextCursor = response == null ? null : response.nextCursor();
            if (nextCursor == null || plays.isEmpty()) {
                break;
            }
            cursor = nextCursor;
            pages++;
        }

        return collected;
    }

    private List<BdlPlateAppearance> fetchPlateAppearances(long gameId, Instant observedAt) {
        rateLimiter.acquire();
        PlateAppearancesRaw fetch = balldontlieClient.getPlateAppearancesRaw(gameId);
        paRawArchiveUploader.upload(gameId, fetch.response(), observedAt);
        return fetch.data();
    }

    private void logCycleResult(long gameId, LiveGameCycleWriter.CycleWriteResult result) {
        if (result.inserted() == 0) {
            return;
        }
        log.info("plays poll completed: gameId={}, inserted={}", gameId, result.inserted());
        PollerRunnerStateMatcher.MatchResult runnerStateResult = result.runnerStateResult();
        log.info(
                "plate appearances matched: gameId={}, updates={}, unmatchedPlateAppearances={}, unmatchedGroups={}",
                gameId,
                runnerStateResult.updates().size(),
                runnerStateResult.unmatchedPlateAppearances(),
                runnerStateResult.unmatchedGroups()
        );
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
