package com.pulse.common.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * scoring.yml 바인딩. 모든 가중치는 scoring.yml에서만 관리한다.
 */
@ConfigurationProperties(prefix = "scoring")
public record ScoringProperties(
        int version,
        LateInning lateInning,
        ScoreGap scoreGap,
        RecentScore recentScore,
        LeadChange leadChange,
        BigInning bigInning,
        CountPressure countPressure,
        Pressure pressure,
        EarlySlugfest earlySlugfest,
        Importance importance,
        int pregameCarryoverMax,
        int personalizationMax,
        Pregame pregame,
        Detail detail,
        Thresholds thresholds
) {
    public record LateInning(int inning78, int inning9, int extra) {}

    public record ScoreGap(int gap01, int gap2, int gap3) {}

    public record RecentScore(int perRun, int max, long tauSeconds, Map<String, Double> closenessMultiplier) {
        public double multiplierFor(int gap) {
            return closenessMultiplier.getOrDefault("gap-" + gap, closenessMultiplier.getOrDefault("default", 1.0));
        }
    }

    public record LeadChange(int bonus, int windowPlays) {}

    public record BigInning(int bonus, int minScoringPlays) {}

    public record CountPressure(int fullCount, int twoOuts, int max) {}

    public record Pressure(int basesLoaded, int scoringPosition) {}

    public record EarlySlugfest(int bonus, int maxInning, int minTotalRuns) {}

    public record Importance(
            double min,
            double max,
            double postseason,
            double bothContending,
            double oneContending,
            double bothOut,
            double contentionMinPercent,
            double contentionMaxPercent
    ) {}

    public record Pregame(
            double closenessMax,
            double closenessWinPercentSpan,
            double closenessImpliedProbabilitySpan,
            double matchupPerStarterMax,
            double matchupEraBaseline,
            double matchupEraSpan,
            double contentionBoth,
            double contentionOne
    ) {}

    public record Detail(
            int hardContactExitVelocity,
            int starterPitchCount,
            double velocityDropMph,
            int velocityDropWindowPitches,
            int longAtBatPitches,
            int maxEventsPerTypePerGame
    ) {}

    public record Thresholds(
            int alertScore,
            int alertRearmScore,
            int alertRiseScore,
            int alertRiseWindowMinutes,
            int alertCooldownMinutes,
            int alertGlobalLimit,
            int alertGlobalWindowMinutes,
            int switchScore,
            int switchGap
    ) {}
}
