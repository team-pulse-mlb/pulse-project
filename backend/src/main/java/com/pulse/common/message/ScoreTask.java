package com.pulse.common.message;

import java.time.Instant;

public record ScoreTask(
        long gameId,
        Instant observedAt,
        Long lastPlayOrder,
        String lifecycleState,
        Situation situation
) {

    public record Situation(
            Integer outs,
            Integer balls,
            Integer strikes,
            boolean runnerOnFirst,
            boolean runnerOnSecond,
            boolean runnerOnThird,
            boolean basesLoaded,
            boolean scoringPosition
    ) {

        public static Situation of(
                Integer outs,
                Integer balls,
                Integer strikes,
                Boolean runnerOnFirst,
                Boolean runnerOnSecond,
                Boolean runnerOnThird
        ) {
            boolean first = Boolean.TRUE.equals(runnerOnFirst);
            boolean second = Boolean.TRUE.equals(runnerOnSecond);
            boolean third = Boolean.TRUE.equals(runnerOnThird);
            return new Situation(
                    outs,
                    balls,
                    strikes,
                    first,
                    second,
                    third,
                    first && second && third,
                    second || third
            );
        }
    }
}
