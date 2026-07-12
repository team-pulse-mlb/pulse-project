package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;

import com.pulse.common.config.ScoringProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AlertSimulator {
    public int simulate(List<Cycle> cycles, ScoringProperties properties) {
        boolean armed = true;
        Instant lastFired = null;
        List<Cycle> history = new ArrayList<>();
        int count = 0;
        for (Cycle cycle : cycles) {
            double score = cycle.watchScore();
            if (score < properties.thresholds().alertRearmScore()) armed = true;
            boolean backfill = "S3_BACKFILL".equals(cycle.source()) || cycle.computedAt() == null;
            boolean cooldown = !backfill && lastFired != null && cycle.computedAt().isBefore(
                    lastFired.plus(Duration.ofMinutes(properties.thresholds().alertCooldownMinutes())));
            boolean entry = armed && score >= properties.thresholds().alertScore();
            boolean rise = !backfill && score >= properties.thresholds().alertScore()
                    && risen(history, cycle, properties);
            if (!cooldown && (entry || rise)) {
                count++;
                armed = false;
                lastFired = cycle.computedAt();
            }
            history.add(cycle);
        }
        return count;
    }

    private boolean risen(List<Cycle> history, Cycle current, ScoringProperties properties) {
        Instant since = current.computedAt().minus(Duration.ofMinutes(properties.thresholds().alertRiseWindowMinutes()));
        return history.stream().filter(cycle -> cycle.computedAt() != null && !cycle.computedAt().isBefore(since))
                .mapToDouble(Cycle::watchScore).min().stream()
                .anyMatch(min -> current.watchScore() - min >= properties.thresholds().alertRiseScore());
    }
}
