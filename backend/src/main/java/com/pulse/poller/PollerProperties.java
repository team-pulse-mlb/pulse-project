package com.pulse.poller;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.poller")
public record PollerProperties(
        boolean enabled,
        Duration tickInterval,
        Duration heartbeatInterval,
        Duration quietThreshold,
        Duration quietPlaysInterval,
        Duration idleGamesInterval,
        Duration scheduledGamesNearThreshold,
        Duration scheduledGamesInterval,
        int slateLookbackDays,
        int slateLookaheadDays,
        int cursorRecoveryEmptyTicks,
        int maxPlayPagesPerGame,
        Duration initialBackoff,
        Duration maxBackoff,
        int rateLimitPerSecond,
        Duration lineupsFarInterval,
        Duration lineupsNearInterval,
        Duration oddsInterval,
        int standingsBatchHourUtc,
        PaArchive paArchive
) {

    public record PaArchive(String bucket, String region) {
    }

    public PollerProperties {
        tickInterval = tickInterval == null ? Duration.ofSeconds(20) : tickInterval;
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(75) : heartbeatInterval;
        quietThreshold = quietThreshold == null ? Duration.ofMinutes(10) : quietThreshold;
        quietPlaysInterval = quietPlaysInterval == null ? Duration.ofMinutes(5) : quietPlaysInterval;
        idleGamesInterval = idleGamesInterval == null ? Duration.ofMinutes(10) : idleGamesInterval;
        scheduledGamesNearThreshold = scheduledGamesNearThreshold == null
                ? Duration.ofMinutes(15) : scheduledGamesNearThreshold;
        scheduledGamesInterval = scheduledGamesInterval == null ? Duration.ofSeconds(20) : scheduledGamesInterval;
        slateLookbackDays = slateLookbackDays < 0 ? 1 : slateLookbackDays;
        slateLookaheadDays = slateLookaheadDays < 0 ? 2 : slateLookaheadDays;
        cursorRecoveryEmptyTicks = cursorRecoveryEmptyTicks <= 0 ? 9 : cursorRecoveryEmptyTicks;
        maxPlayPagesPerGame = maxPlayPagesPerGame <= 0 ? 5 : maxPlayPagesPerGame;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(5) : maxBackoff;
        rateLimitPerSecond = rateLimitPerSecond <= 0 ? 10 : rateLimitPerSecond;
        lineupsFarInterval = lineupsFarInterval == null ? Duration.ofHours(1) : lineupsFarInterval;
        lineupsNearInterval = lineupsNearInterval == null ? Duration.ofMinutes(15) : lineupsNearInterval;
        oddsInterval = oddsInterval == null ? Duration.ofMinutes(30) : oddsInterval;
        standingsBatchHourUtc = standingsBatchHourUtc <= 0 ? 10 : standingsBatchHourUtc;
        paArchive = paArchive == null ? new PaArchive(null, null) : paArchive;
    }
}
