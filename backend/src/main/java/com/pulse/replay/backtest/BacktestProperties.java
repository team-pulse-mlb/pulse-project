package com.pulse.replay.backtest;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.backtest")
public record BacktestProperties(
        String baseline,
        String candidate,
        LocalDate from,
        LocalDate to,
        List<Long> gameIds,
        List<String> sources,
        String outputDir,
        int backfillRecentScoreWindowPlays,
        int backfillLeadChangeWindowPlays,
        int aucHorizonPlays,
        int topN,
        double guardRankCorrelationMin,
        double guardAucDropMax,
        double guardDailyAlertMax,
        double guardDailyAlertRatioMax
) {
    public BacktestProperties {
        gameIds = gameIds == null ? List.of() : List.copyOf(gameIds);
        sources = sources == null ? List.of() : List.copyOf(sources);
        outputDir = outputDir == null || outputDir.isBlank() ? "../docs/backtest" : outputDir;
        backfillRecentScoreWindowPlays = positive(backfillRecentScoreWindowPlays, 15);
        backfillLeadChangeWindowPlays = positive(backfillLeadChangeWindowPlays, 25);
        aucHorizonPlays = positive(aucHorizonPlays, 12);
        topN = positive(topN, 10);
        guardRankCorrelationMin = defaultDouble(guardRankCorrelationMin, 0.7);
        guardAucDropMax = defaultDouble(guardAucDropMax, 0.02);
        guardDailyAlertMax = defaultDouble(guardDailyAlertMax, 5.0);
        guardDailyAlertRatioMax = defaultDouble(guardDailyAlertRatioMax, 2.0);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static double defaultDouble(double value, double fallback) {
        return value > 0 ? value : fallback;
    }
}
