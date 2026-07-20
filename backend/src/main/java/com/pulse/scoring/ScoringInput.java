package com.pulse.scoring;

import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.List;

/** live, backtest, rescore가 공통 계산기에 전달하는 점수 입력 스냅샷. */
public record ScoringInput(
        Game game,
        List<Play> recentPlays,
        ScoreTask.Situation situation,
        int seedLeader,
        Instant computedAt,
        double importanceMultiplier,
        double pregameScore,
        ScoreTask.GameSnapshot gameSnapshot
) {
    public ScoringInput(
            Game game,
            List<Play> recentPlays,
            ScoreTask.Situation situation,
            int seedLeader,
            Instant computedAt,
            double importanceMultiplier,
            double pregameScore
    ) {
        this(game, recentPlays, situation, seedLeader, computedAt, importanceMultiplier, pregameScore, null);
    }

    public ScoringInput {
        recentPlays = List.copyOf(recentPlays);
    }
}
