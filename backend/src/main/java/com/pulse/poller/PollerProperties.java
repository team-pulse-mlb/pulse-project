package com.pulse.poller;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.poller")
public record PollerProperties(
        boolean enabled,
        Duration tickInterval,
        Duration idleGamesInterval,
        int slateLookbackDays,
        int slateLookaheadDays,
        int maxPlayPagesPerGame,
        Duration initialBackoff,
        Duration maxBackoff,
        int rateLimitPerSecond,
        Duration lineupsFarInterval,
        Duration lineupsNearInterval,
        Duration oddsInterval,
        int standingsBatchHourUtc
) {

    public PollerProperties {
        tickInterval = tickInterval == null ? Duration.ofSeconds(20) : tickInterval;
        idleGamesInterval = idleGamesInterval == null ? Duration.ofMinutes(10) : idleGamesInterval;
        slateLookbackDays = slateLookbackDays < 0 ? 1 : slateLookbackDays;
        slateLookaheadDays = slateLookaheadDays < 0 ? 1 : slateLookaheadDays;
        maxPlayPagesPerGame = maxPlayPagesPerGame <= 0 ? 5 : maxPlayPagesPerGame;
        initialBackoff = initialBackoff == null ? Duration.ofSeconds(30) : initialBackoff;
        maxBackoff = maxBackoff == null ? Duration.ofMinutes(5) : maxBackoff;
        rateLimitPerSecond = rateLimitPerSecond <= 0 ? 10 : rateLimitPerSecond;
        lineupsFarInterval = lineupsFarInterval == null ? Duration.ofHours(1) : lineupsFarInterval;
        lineupsNearInterval = lineupsNearInterval == null ? Duration.ofMinutes(15) : lineupsNearInterval;
        oddsInterval = oddsInterval == null ? Duration.ofMinutes(30) : oddsInterval;
        standingsBatchHourUtc = standingsBatchHourUtc <= 0 ? 10 : standingsBatchHourUtc;
    }
}
