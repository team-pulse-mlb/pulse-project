package com.pulse.poller;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.poller", name = "enabled", havingValue = "true")
public class OperationalPoller {

    private final GameListSynchronizer gameListSynchronizer;
    private final LivePlaysPoller livePlaysPoller;
    private final Clock clock;

    @Autowired
    public OperationalPoller(
            BaseballDataSource balldontlieClient,
            GameRepository gameRepository,
            PollerGameWriter gameWriter,
            LiveGameCycleWriter liveGameCycleWriter,
            ScoreTaskFactory scoreTaskFactory,
            ScoreTaskPublisher scoreTaskPublisher,
            NotificationEventPublisher notificationEventPublisher,
            GameTransitionWriter gameTransitionWriter,
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
                gameTransitionWriter,
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
            GameTransitionWriter gameTransitionWriter,
            PollerProperties properties,
            PollerRateLimiter rateLimiter,
            PaRawArchiveUploader paRawArchiveUploader,
            Clock clock
    ) {
        PollerLiveGameStateTracker stateTracker = new PollerLiveGameStateTracker(properties);
        LivePlaysPoller assembledLivePlaysPoller = new LivePlaysPoller(
                balldontlieClient,
                gameWriter,
                liveGameCycleWriter,
                properties,
                rateLimiter,
                paRawArchiveUploader,
                stateTracker
        );
        this.gameListSynchronizer = new GameListSynchronizer(
                balldontlieClient,
                gameRepository,
                properties,
                rateLimiter,
                stateTracker,
                assembledLivePlaysPoller,
                gameTransitionWriter
        );
        this.livePlaysPoller = assembledLivePlaysPoller;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${pulse.poller.tick-delay-ms:20000}")
    public void poll() {
        PulseMetrics.increment("pulse.poller.ticks", "poller", "operational");
        Instant now = clock.instant();
        List<Game> liveGames = gameListSynchronizer.syncGames(now);
        livePlaysPoller.pollLiveGames(liveGames, now);
    }

    @PreDestroy
    void shutdown() {
        livePlaysPoller.shutdown();
    }
}
