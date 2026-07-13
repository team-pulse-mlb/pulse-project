package com.pulse.poller.simulation;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.simulation")
public record SimulationProperties(
        boolean enabled, Long sourceGameId, Long targetGameId, List<GameSpec> games, double speed,
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
        games = games == null ? List.of() : List.copyOf(games);
        if (maxArchiveObjects <= 0) maxArchiveObjects = 1000;
    }

    public long requiredSourceGameId() {
        if (sourceGameId == null) throw new IllegalStateException("pulse.simulation.source-game-id is required");
        return sourceGameId;
    }

    public long resolvedTargetGameId() {
        return targetGameId == null ? requiredSourceGameId() + 9_000_000_000L : targetGameId;
    }

    public List<ResolvedGameSpec> resolvedGames() {
        List<ResolvedGameSpec> resolved = games.isEmpty()
                ? List.of(new ResolvedGameSpec(requiredSourceGameId(), resolvedTargetGameId(), startOffset, preset))
                : games.stream().map(SimulationProperties::resolve).toList();

        Set<Long> targetGameIds = new HashSet<>();
        for (ResolvedGameSpec game : resolved) {
            if (game.sourceGameId() == game.targetGameId()) {
                throw new IllegalStateException("simulation target game id must differ from source game id");
            }
            if (!targetGameIds.add(game.targetGameId())) {
                throw new IllegalStateException("simulation target game ids must be unique");
            }
        }
        return resolved;
    }

    private static ResolvedGameSpec resolve(GameSpec game) {
        if (game.sourceGameId() == null) {
            throw new IllegalStateException("pulse.simulation.games[].source-game-id is required");
        }
        long sourceGameId = game.sourceGameId();
        long targetGameId = game.targetGameId() == null ? sourceGameId + 9_000_000_000L : game.targetGameId();
        Duration startOffset = game.startOffset() == null ? Duration.ZERO : game.startOffset();
        String preset = game.preset() == null ? "START" : game.preset().trim().toUpperCase();
        return new ResolvedGameSpec(sourceGameId, targetGameId, startOffset, preset);
    }

    /**
     * @param startOffset 양수는 데모 시작 시점에 이미 진행된 시간이며, 음수는 첫 플레이가 미래에 시작됨을 의미한다.
     */
    public record GameSpec(
            Long sourceGameId,
            Long targetGameId,
            Duration startOffset,
            String preset
    ) {}

    public record ResolvedGameSpec(long sourceGameId, long targetGameId, Duration startOffset, String preset) {}

    public record S3(String bucket, String region) {}
}
