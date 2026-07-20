package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;

import com.pulse.common.config.ScoringProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlertSimulator {
    public int simulate(List<Cycle> cycles, ScoringProperties properties) {
        return simulate(Map.of(0L, cycles), properties).getOrDefault(0L, 0);
    }

    public Map<Long, Integer> simulate(Map<Long, List<Cycle>> cyclesByGame, ScoringProperties properties) {
        Map<Long, GameState> states = new HashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        cyclesByGame.keySet().forEach(gameId -> counts.put(gameId, 0));

        List<GameCycle> orderedCycles = cyclesByGame.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(cycle -> new GameCycle(entry.getKey(), cycle)))
                .sorted(Comparator
                        .comparing((GameCycle item) -> item.cycle().computedAt(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(GameCycle::gameId)
                        .thenComparingLong(item -> item.cycle().playOrder()))
                .toList();

        GlobalWindow globalWindow = new GlobalWindow();
        for (GameCycle item : orderedCycles) {
            long gameId = item.gameId();
            Cycle cycle = item.cycle();
            GameState state = states.computeIfAbsent(gameId, ignored -> new GameState());
            // 운영 저장값과 히스테리시스 이력을 재현하도록 정수 반올림 점수로 판정한다.
            int score = (int) Math.round(cycle.watchScore());
            if (score < properties.thresholds().alertRearmScore()) state.armed = true;
            boolean backfill = "S3_BACKFILL".equals(cycle.source()) || cycle.computedAt() == null;
            boolean cooldown = !backfill && state.lastFired != null && cycle.computedAt().isBefore(
                    state.lastFired.plus(Duration.ofMinutes(properties.thresholds().alertCooldownMinutes())));
            boolean entry = state.armed && score >= properties.thresholds().alertScore();
            boolean rise = !backfill && score >= properties.thresholds().alertScore()
                    && risen(state.history, cycle, properties);
            boolean globallyLimited = !backfill && globalWindow.isLimited(cycle.computedAt(), properties);
            if (!cooldown && !globallyLimited && (entry || rise)) {
                counts.compute(gameId, (ignored, count) -> count == null ? 1 : count + 1);
                state.armed = false;
                state.lastFired = cycle.computedAt();
                if (!backfill) globalWindow.record(cycle.computedAt(), properties);
            }
            state.history.add(cycle);
        }
        return counts;
    }

    private boolean risen(List<Cycle> history, Cycle current, ScoringProperties properties) {
        Instant since = current.computedAt().minus(Duration.ofMinutes(properties.thresholds().alertRiseWindowMinutes()));
        int currentScore = (int) Math.round(current.watchScore());
        return history.stream().filter(cycle -> cycle.computedAt() != null && !cycle.computedAt().isBefore(since))
                .mapToInt(cycle -> (int) Math.round(cycle.watchScore())).min().stream()
                .anyMatch(min -> currentScore - min >= properties.thresholds().alertRiseScore());
    }

    private record GameCycle(long gameId, Cycle cycle) {}

    private static final class GameState {
        private boolean armed = true;
        private Instant lastFired;
        private final List<Cycle> history = new ArrayList<>();
    }

    private static final class GlobalWindow {
        private int count;
        private Instant expiresAt;

        private boolean isLimited(Instant now, ScoringProperties properties) {
            resetIfExpired(now);
            return count >= properties.thresholds().alertGlobalLimit();
        }

        private void record(Instant now, ScoringProperties properties) {
            resetIfExpired(now);
            count++;
            if (count == 1) {
                expiresAt = now.plus(Duration.ofMinutes(properties.thresholds().alertGlobalWindowMinutes()));
            }
        }

        private void resetIfExpired(Instant now) {
            if (expiresAt != null && !now.isBefore(expiresAt)) {
                count = 0;
                expiresAt = null;
            }
        }
    }
}
