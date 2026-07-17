package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.client.BdlDtos.PlateAppearancesRaw;
import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.PollerGameWriter.GameUpsertResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class OperationalPoller {

    private final BaseballDataSource balldontlieClient;
    private final GameRepository gameRepository;
    private final PollerGameWriter gameWriter;
    private final LiveGameCycleWriter liveGameCycleWriter;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final PaRawArchiveUploader paRawArchiveUploader;
    private final Clock clock;
    private final PollerBackoff gamesBackoff;
    private final PollerBackoff playsBackoff;

    private Instant nextGamesPollAt = Instant.EPOCH;

    @Autowired
    public OperationalPoller(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PollerGameWriter gameWriter,
            LiveGameCycleWriter liveGameCycleWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PaRawArchiveUploader paRawArchiveUploader
    ) {
        this(
                balldontlieClient,
                gameRepository,
                gameWriter,
                liveGameCycleWriter,
                scoreTaskFactory,
                scoreTaskPublisher,
                notificationEventPublisher,
                properties,
                rateLimiter,
                paRawArchiveUploader,
                Clock.systemUTC()
        );
    }

    OperationalPoller(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PollerGameWriter gameWriter,
            LiveGameCycleWriter liveGameCycleWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PaRawArchiveUploader paRawArchiveUploader,
            Clock clock
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.gameWriter = gameWriter;
        this.liveGameCycleWriter = liveGameCycleWriter;
        this.scoreTaskFactory = scoreTaskFactory;
        this.scoreTaskPublisher = scoreTaskPublisher;
        this.notificationEventPublisher = notificationEventPublisher;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.paRawArchiveUploader = paRawArchiveUploader;
        this.clock = clock;
        this.gamesBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
        this.playsBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    @Scheduled(fixedDelayString = "${pulse.poller.tick-delay-ms:20000}")
    public void poll() {
        PulseMetrics.increment("pulse.poller.ticks", "poller", "operational");
        Instant now = clock.instant();
        List<Game> liveGames = now.isBefore(nextGamesPollAt)
                ? gameRepository.findByLifecycleState(GameLifecycle.LIVE.name())
                : syncGames(now);

        pollLiveGames(liveGames, now);
    }

    private List<Game> syncGames(Instant now) {
        if (!gamesBackoff.canCall(now)) {
            log.info("games poll skipped by backoff until {}", gamesBackoff.blockedUntil());
            return gameRepository.findByLifecycleState(GameLifecycle.LIVE.name());
        }

        Map<Long, Game> liveGames = new LinkedHashMap<>();
        int changedGames = 0;
        try {
            for (LocalDate date : slateDates(now)) {
                rateLimiter.acquire();
                for (BdlGame dto : balldontlieClient.getGames(date)) {
                    GameUpsertResult result = gameWriter.upsertGame(dto, now);
                    changedGames++;
                    if (GameLifecycle.LIVE.name().equals(result.currentLifecycle())) {
                        liveGames.put(result.game().getId(), result.game());
                    }
                    boolean terminalScoreTaskPublished = result.enteredTerminalState()
                            && drainTerminalGame(result.game(), now);
                    publishTransitionEvents(result, terminalTaskObservedAt(now, terminalScoreTaskPublished));
                }
            }
            gamesBackoff.recordSuccess();
            boolean hasLiveGame = !liveGames.isEmpty()
                    || !gameRepository.findByLifecycleState(GameLifecycle.LIVE.name()).isEmpty();
            nextGamesPollAt = now.plus(hasLiveGame ? properties.tickInterval() : properties.idleGamesInterval());
            log.info("games poll completed: changedGames={}, liveGames={}", changedGames, liveGames.size());
        } catch (RuntimeException e) {
            handleFailure("games", gamesBackoff, now, e);
        }

        if (liveGames.isEmpty()) {
            return gameRepository.findByLifecycleState(GameLifecycle.LIVE.name());
        }
        return new ArrayList<>(liveGames.values());
    }

    private void pollLiveGames(List<Game> liveGames, Instant now) {
        if (liveGames.isEmpty() || !playsBackoff.canCall(now)) {
            return;
        }

        for (Game game : liveGames) {
            try {
                List<BdlPlay> plays = fetchPlays(game);
                if (!plays.isEmpty()) {
                    List<BdlPlateAppearance> plateAppearances = fetchPlateAppearances(game.getId(), now);
                    LiveGameCycleWriter.CycleWriteResult result =
                            liveGameCycleWriter.write(game, plays, plateAppearances, now);
                    logCycleResult(game.getId(), result);
                }
            } catch (RuntimeException e) {
                if (PollerExceptionClassifier.shouldBackoff(e)) {
                    handleFailure("plays", playsBackoff, now, e);
                    return;
                }
                log.error("plays poll failed: gameId={}", game.getId(), e);
                PulseMetrics.increment("pulse.poller.game.skips", "reason", "isolated_failure");
            }
        }
        playsBackoff.recordSuccess();
    }

    private boolean drainTerminalGame(Game game, Instant now) {
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

    private static Instant terminalTaskObservedAt(Instant now, boolean scoreTaskPublished) {
        // 같은 tick의 live/terminal task가 outbox (game_id, observed_at) 고유키에서 충돌하지 않게 한다.
        return scoreTaskPublished ? now.plusMillis(1) : now;
    }

    private List<BdlPlay> fetchPlays(Game game) {
        Long cursor = game.getLastPlayOrder();
        List<BdlPlay> collected = new ArrayList<>();
        int pages = 0;

        while (pages < properties.maxPlayPagesPerGame()) {
            rateLimiter.acquire();
            ListResponse<BdlPlay> response = balldontlieClient.getPlays(game.getId(), cursor);
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

    private void publishTransitionEvents(GameUpsertResult result, Instant now) {
        if (result.enteredLive()) {
            UUID eventId = UUID.randomUUID();
            notificationEventPublisher.publish(new NotificationEvent(
                    eventId,
                    NotificationType.GAME_START,
                    result.game().getId(),
                    gameStartMessage(result.game()),
                    null,
                    now
            ));
        }
        if (result.enteredTerminalState()) {
            scoreTaskPublisher.publish(scoreTaskFactory.terminalTask(result.game(), now));
        }
    }

    private static String gameStartMessage(Game game) {
        return "관심 팀 경기가 시작됐어요 — "
                + teamLabel(game.getAwayTeamAbbr(), game.getAwayTeamName())
                + " @ "
                + teamLabel(game.getHomeTeamAbbr(), game.getHomeTeamName());
    }

    private static String teamLabel(String abbreviation, String name) {
        if (abbreviation != null && !abbreviation.isBlank()) {
            return abbreviation;
        }
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "미정";
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
