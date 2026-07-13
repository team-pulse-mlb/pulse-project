package com.pulse.common.ai;

import java.util.List;

public record FinalHeadlineContext(
        long gameId,
        AiCopyMode mode,
        String status,
        String periodLabel,
        List<String> reasonTags,
        List<String> spoilerSafeSignals,
        List<KeyMoment> keyMoments,
        FinalScore finalScore,
        String winner,
        String contextHash
) {
    public record KeyMoment(Integer inning, String label) {
    }

    public record FinalScore(Integer home, Integer away) {
    }
}
