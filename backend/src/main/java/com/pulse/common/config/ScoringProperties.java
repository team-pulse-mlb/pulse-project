package com.pulse.common.config;

import java.util.List;
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
        Personalization personalization,
        Pregame pregame,
        Detail detail,
        TensionCurve tensionCurve,
        Thresholds thresholds,
        Highlight highlight
) {
    public record LateInning(int inning78, int inning9, int extra) {}

    public record ScoreGap(int gap01, int gap2, int gap3) {}

    // baseBudget은 closeness multiplier 적용 전의 예산이다. 실제 신호 상한이 아니라
    // multiplier(최대 2.0)에 따라 신호는 baseBudget의 최대 2배까지 커질 수 있다.
    public record RecentScore(int perRun, int baseBudget, long tauSeconds, Map<String, Double> closenessMultiplier) {
        public double multiplierFor(int gap) {
            return closenessMultiplier.getOrDefault("gap-" + gap, closenessMultiplier.getOrDefault("default", 1.0));
        }
    }

    public record LeadChange(int bonus, int windowPlays, long tauSeconds) {}

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

    public record Personalization(int teamBonus, int playerBonus, int max) {}

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

    public record TensionCurve(List<Integer> levelBoundaries) {
        public TensionCurve {
            levelBoundaries = List.copyOf(levelBoundaries);
            if (levelBoundaries.size() != 4) {
                throw new IllegalArgumentException("경기 긴장도 그래프 경계값은 4개여야 합니다.");
            }
            for (int index = 0; index < levelBoundaries.size(); index++) {
                int boundary = levelBoundaries.get(index);
                if (boundary < 0 || boundary > 100
                        || (index > 0 && boundary <= levelBoundaries.get(index - 1))) {
                    throw new IllegalArgumentException("경기 긴장도 그래프 경계값은 0~100 사이에서 오름차순이어야 합니다.");
                }
            }
        }
    }

    /**
     * 보호 모드 타임라인 하이라이트 트리거 설정.
     * 알림용 Thresholds와 분리해, 타임라인은 더 촘촘히 하이라이트를 남길 수 있게 한다.
     *
     * enabled=false면 기존 이벤트별 문구 생성 트리거를 그대로 사용한다(안전한 롤아웃 스위치).
     * backfillMaxPerGame은 종료 백필에서 경기당 생성할 하이라이트의 상한이다.
     */
    public record Highlight(
            boolean enabled,
            int minScore,
            int riseScore,
            int riseWindowMinutes,
            int cooldownMinutes,
            int backfillMaxPerGame
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
