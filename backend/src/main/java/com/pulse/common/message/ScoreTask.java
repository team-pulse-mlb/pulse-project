package com.pulse.common.message;

import java.time.Instant;
import java.util.List;

public record ScoreTask(
        long gameId,
        Instant observedAt,
        Long lastPlayOrder,
        String lifecycleState,
        Situation situation,
        List<PlateAppearanceSnapshot> plateAppearances
) {

    public ScoreTask(
            long gameId,
            Instant observedAt,
            Long lastPlayOrder,
            String lifecycleState,
            Situation situation
    ) {
        this(gameId, observedAt, lastPlayOrder, lifecycleState, situation, List.of());
    }

    public ScoreTask {
        plateAppearances = plateAppearances == null ? List.of() : List.copyOf(plateAppearances);
    }

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

    /** PA 원본 중 이벤트 추출에 필요한 사실만 담는다. 결과·설명 원문은 브로커로 보내지 않는다. */
    public record PlateAppearanceSnapshot(
            long paNumber,
            Integer inning,
            String inningType,
            Long batterId,
            Long pitcherId,
            Integer outs,
            boolean runnerOnFirst,
            boolean runnerOnSecond,
            boolean runnerOnThird,
            List<PitchSnapshot> pitches
    ) {

        public PlateAppearanceSnapshot {
            pitches = pitches == null ? List.of() : List.copyOf(pitches);
        }
    }

    public record PitchSnapshot(
            Integer pitchNumber,
            Integer pitcherPitchCount,
            Double releaseSpeed,
            Double exitVelocity,
            boolean barrel
    ) {
    }
}
