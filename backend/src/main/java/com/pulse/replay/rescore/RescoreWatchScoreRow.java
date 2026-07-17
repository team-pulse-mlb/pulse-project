package com.pulse.replay.rescore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

record RescoreWatchScoreRow(
        Long gameId,
        Instant computedAt,
        Long playOrder,
        Integer inning,
        String inningType,
        int baseScore,
        BigDecimal importanceMultiplier,
        BigDecimal pregameBonus,
        int watchScore,
        int scoringVersion,
        Map<String, Double> signalContributions,
        List<String> tags,
        boolean backfilled,
        String source
) {
}
