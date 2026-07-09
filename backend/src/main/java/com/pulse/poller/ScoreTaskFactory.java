package com.pulse.poller;

import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class ScoreTaskFactory {

    /** 경기 전 계산 task의 lifecycleState 값. GameLifecycle 상태가 아니라 계산 분기 표식이다. */
    public static final String PREGAME_LIFECYCLE = "PREGAME";

    public ScoreTask pregameTask(Game game, Instant observedAt) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                null,
                PREGAME_LIFECYCLE,
                null
        );
    }

    public ScoreTask liveTask(Game game, Play latestPlay, Instant observedAt) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                latestPlay == null ? game.getLastPlayOrder() : latestPlay.getPlayOrder(),
                game.getLifecycleState(),
                latestPlay == null ? null : situation(latestPlay)
        );
    }

    public ScoreTask terminalTask(Game game, Instant observedAt) {
        return new ScoreTask(
                game.getId(),
                observedAt,
                game.getLastPlayOrder(),
                game.getLifecycleState(),
                null
        );
    }

    private static ScoreTask.Situation situation(Play play) {
        return ScoreTask.Situation.of(
                play.getOuts(),
                play.getBalls(),
                play.getStrikes(),
                play.getRunnerOnFirst(),
                play.getRunnerOnSecond(),
                play.getRunnerOnThird()
        );
    }
}
