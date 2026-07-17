package com.pulse.replay.rescore;

import java.time.Instant;

record RescorePlayRow(
        Long gameId,
        Long playOrder,
        String type,
        Integer inning,
        String inningType,
        Integer homeScore,
        Integer awayScore,
        Boolean scoringPlay,
        Integer scoreValue,
        Integer outs,
        Integer balls,
        Integer strikes,
        Boolean runnerOnFirst,
        Boolean runnerOnSecond,
        Boolean runnerOnThird,
        Instant observedAt,
        Boolean backfilled,
        String source
) {
    boolean backfilledValue() {
        return Boolean.TRUE.equals(backfilled);
    }

    String sourceValue() {
        return source == null ? "OPERATIONAL" : source;
    }
}
