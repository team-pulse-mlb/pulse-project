package com.pulse.replay.rescore;

import java.time.Instant;
import java.util.List;

record RescoreScorePoint(
        Long gameId,
        Instant computedAt,
        Long playOrder,
        Integer inning,
        String inningType,
        int baseScore,
        List<String> tags,
        String source
) {
}
