package com.pulse.poller.simulation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.simulation")
public record SimulationProperties(
        boolean enabled, Long sourceGameId, Long targetGameId, double speed,
        Duration startOffset, Duration playInterval, String preset,
        String archiveDate, S3 s3, int maxArchiveObjects
) {
    public SimulationProperties {
        if (speed != 1.0 && speed != 5.0 && speed != 20.0) {
            throw new IllegalArgumentException("pulse.simulation.speed must be one of 1, 5, 20");
        }
        startOffset = startOffset == null ? Duration.ZERO : startOffset;
        playInterval = playInterval == null ? Duration.ofSeconds(20) : playInterval;
        if (startOffset.isNegative()) throw new IllegalArgumentException("pulse.simulation.start-offset must not be negative");
        if (playInterval.isZero() || playInterval.isNegative()) throw new IllegalArgumentException("pulse.simulation.play-interval must be positive");
        preset = preset == null ? "START" : preset.trim().toUpperCase();
        if (maxArchiveObjects <= 0) maxArchiveObjects = 1000;
    }

    public long requiredSourceGameId() {
        if (sourceGameId == null) throw new IllegalStateException("pulse.simulation.source-game-id is required");
        return sourceGameId;
    }

    public long resolvedTargetGameId() {
        return targetGameId == null ? requiredSourceGameId() + 9_000_000_000L : targetGameId;
    }

    public record S3(String bucket, String region) {}
}
