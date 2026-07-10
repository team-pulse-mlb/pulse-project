package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import java.util.Map;

final class TestScoringProperties {

    private TestScoringProperties() {
    }

    static ScoringProperties version3() {
        return new ScoringProperties(
                3,
                new ScoringProperties.LateInning(6, 12, 18),
                new ScoringProperties.ScoreGap(15, 9, 3),
                new ScoringProperties.RecentScore(6, 15, 180,
                        Map.of("gap-0", 2.0, "gap-1", 1.5, "gap-2", 1.2, "default", 1.0)),
                new ScoringProperties.LeadChange(9, 12),
                new ScoringProperties.BigInning(9, 2),
                new ScoringProperties.CountPressure(3, 3, 5),
                new ScoringProperties.Pressure(6, 4),
                new ScoringProperties.EarlySlugfest(5, 3, 7),
                new ScoringProperties.Importance(0.9, 1.15, 1.15, 1.10, 1.05, 0.90, 10, 90),
                10,
                15,
                new ScoringProperties.Pregame(30, 0.15, 0.25, 20, 4.6, 2.0, 30, 15),
                new ScoringProperties.Detail(100, 100, 2.0, 10, 8),
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 70, 20)
        );
    }
}
