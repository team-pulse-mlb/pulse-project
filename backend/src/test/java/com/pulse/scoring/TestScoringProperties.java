package com.pulse.scoring;

import com.pulse.common.config.ScoringProperties;
import java.util.List;
import java.util.Map;

public final class TestScoringProperties {

    private TestScoringProperties() {
    }

    public static ScoringProperties version5() {
        return version5(defaultPressure(), defaultHighlight());
    }

    public static ScoringProperties version5(ScoringProperties.Highlight highlight) {
        return version5(defaultPressure(), highlight);
    }

    /** RE24 테이블 등 pressure 설정만 바꿔 검증할 때 사용한다. */
    public static ScoringProperties version5(ScoringProperties.Pressure pressure) {
        return version5(pressure, defaultHighlight());
    }

    private static ScoringProperties.Pressure defaultPressure() {
        return new ScoringProperties.Pressure(6, 4, 0, null);
    }

    private static ScoringProperties.Highlight defaultHighlight() {
        return new ScoringProperties.Highlight(false, 40, 12, 6, 8, 8);
    }

    public static ScoringProperties version5(ScoringProperties.Pressure pressure, ScoringProperties.Highlight highlight) {
        return new ScoringProperties(
                5,
                new ScoringProperties.LateInning(6, 12, 18),
                new ScoringProperties.ScoreGap(15, 9, 3),
                new ScoringProperties.RecentScore(6, 15, 180,
                        Map.of("gap-0", 2.0, "gap-1", 1.5, "gap-2", 1.2, "default", 1.0)),
                new ScoringProperties.LeadChange(9, 12, 300),
                new ScoringProperties.BigInning(9, 2),
                new ScoringProperties.CountPressure(3, 3, 5),
                pressure,
                new ScoringProperties.EarlySlugfest(5, 3, 7),
                new ScoringProperties.Importance(0.9, 1.15, 1.15, 1.10, 1.05, 0.90, 10, 90),
                10,
                new ScoringProperties.Personalization(10, 5, 15),
                new ScoringProperties.Pregame(30, 0.15, 0.25, 20, 4.6, 2.0, 30, 15),
                new ScoringProperties.Detail(100, 100, 2.0, 10, 8, 8),
                new ScoringProperties.TensionCurve(List.of(20, 40, 60, 80)),
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 3, 15, 70, 20),
                highlight
        );
    }
}
