package com.pulse.poller;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
@Slf4j
public class OperationalPoller {

    private final BalldontlieClient balldontlieClient;
    private final GameRepository gameRepository;
    private final PlayRepository playRepository;
    private final PollerGameWriter gameWriter;
    private final ScoreTaskFactory scoreTaskFactory;
    private final ScoreTaskPublisher scoreTaskPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final PollerProperties properties;
    private final PollerRateLimiter rateLimiter;
    private final Clock clock;
    private final PollerBackoff gamesBackoff;
    private final PollerBackoff playsBackoff;

    private Instant nextGamesPollAt = Instant.EPOCH;

    public OperationalPoller(
            BalldontlieClient balldontlieClient,
            GameRepository gameRepository,
            PlayRepository playRepository,
            PollerGameWriter gameWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter
    ) {
        this(
                balldontlieClient,
                gameRepository,
                playRepository,
                gameWriter,
                scoreTaskFactory,
                scoreTaskPublisher,
                notificationEventPublisher,
                properties,
                rateLimiter,
                Clock.systemUTC()
        );
    }

    OperationalPoller(
            BalldontlieClient balldontlieClient,
            GameRepository gameRepository,
            PlayRepository playRepository,
            PollerGameWriter gameWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            Clock clock
    ) {
        this.balldontlieClient = balldontlieClient;
        this.gameRepository = gameRepository;
        this.playRepository = playRepository;
        this.gameWriter = gameWriter;
        this.scoreTaskFactory = scoreTaskFactory;
        this.scoreTaskPublisher = scoreTaskPublisher;
        this.notificationEventPublisher = notificationEventPublisher;
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
        this.gamesBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
        this.playsBackoff = new PollerBackoff(properties.initialBackoff(), properties.maxBackoff());
    }

    @Scheduled(fixedDelayString = "${pulse.poller.tick-delay-ms:20000}")
    public void poll() {
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
                    publishTransitionEvents(result, now);
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

        try {
            for (Game game : liveGames) {
                int inserted = pollPlays(game, now);
                if (inserted > 0) {
                    syncPlateAppearances(game.getId());
                    latestPlay(game.getId()).ifPresent(play ->
                            scoreTaskPublisher.publish(scoreTaskFactory.liveTask(game, play, now)));
                }
            }
            playsBackoff.recordSuccess();
        } catch (RuntimeException e) {
            handleFailure("plays", playsBackoff, now, e);
        }
    }

    private int pollPlays(Game game, Instant observedAt) {
        Long cursor = game.getLastPlayOrder();
        int inserted = 0;
        int pages = 0;

        while (pages < properties.maxPlayPagesPerGame()) {
            rateLimiter.acquire();
            ListResponse<BdlPlay> response = balldontlieClient.getPlays(game.getId(), cursor);
            List<BdlPlay> plays = response == null || response.data() == null ? List.of() : response.data();
            for (BdlPlay play : plays) {
                if (gameWriter.appendPlay(game, play, observedAt)) {
                    inserted++;
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

        if (inserted > 0) {
            log.info("plays poll completed: gameId={}, inserted={}", game.getId(), inserted);
        }
        return inserted;
    }

    private void syncPlateAppearances(long gameId) {
        rateLimiter.acquire();
        List<BdlPlateAppearance> plateAppearances = balldontlieClient.getPlateAppearances(gameId);
        PollerRunnerStateMatcher.MatchResult result = gameWriter.updateRunnerStates(gameId, plateAppearances);
        log.info(
                "plate appearances matched: gameId={}, updates={}, unmatchedPlateAppearances={}, unmatchedGroups={}",
                gameId,
                result.updates().size(),
                result.unmatchedPlateAppearances(),
                result.unmatchedGroups()
        );
    }

    private void publishTransitionEvents(GameUpsertResult result, Instant now) {
        if (result.enteredLive()) {
            notificationEventPublisher.publish(new NotificationEvent(
                    UUID.randomUUID(),
                    NotificationType.GAME_START,
                    result.game().getId(),
                    List.of(),
                    now
            ));
        }
        if (result.enteredTerminalState()) {
            scoreTaskPublisher.publish(scoreTaskFactory.terminalTask(result.game(), now));
        }
    }

    private List<LocalDate> slateDates(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        List<LocalDate> dates = new ArrayList<>();
        for (int offset = -properties.slateLookbackDays(); offset <= properties.slateLookaheadDays(); offset++) {
            dates.add(today.plusDays(offset));
        }
        return dates;
    }

    private java.util.Optional<Play> latestPlay(long gameId) {
        return playRepository.findByGameIdOrderByPlayOrderDesc(gameId, PageRequest.of(0, 1))
                .stream()
                .findFirst();
    }

    private void handleFailure(String target, PollerBackoff backoff, Instant now, RuntimeException e) {
        if (PollerExceptionClassifier.shouldBackoff(e)) {
            backoff.recordFailure(now, PollerExceptionClassifier.retryAfter(e));
            log.warn("{} poll failed, backed off until {}", target, backoff.blockedUntil(), e);
            return;
        }
        throw e;
    }
}
