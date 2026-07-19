package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoreTaskFactoryTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");

    private final ScoreTaskFactory factory = new ScoreTaskFactory();

    @Test
    void pregameTask_shouldIncludeGameSnapshot() {
        Game game = game();

        ScoreTask task = factory.pregameTask(game, OBSERVED_AT);

        assertThat(task.gameSnapshot()).isEqualTo(expectedSnapshot());
    }

    @Test
    void liveTask_shouldIncludeGameSnapshot() {
        Game game = game();
        Play latestPlay = new Play();
        latestPlay.setPlayOrder(12L);

        ScoreTask task = factory.liveTask(game, latestPlay, OBSERVED_AT, List.of(), "LIVE");

        assertThat(task.gameSnapshot()).isEqualTo(expectedSnapshot());
    }

    @Test
    void terminalTask_shouldIncludeGameSnapshot() {
        Game game = game();

        ScoreTask task = factory.terminalTask(game, OBSERVED_AT);

        assertThat(task.gameSnapshot()).isEqualTo(expectedSnapshot());
    }

    private static Game game() {
        Game game = new Game();
        game.setId(1L);
        game.setLifecycleState("LIVE");
        game.setLastPlayOrder(12L);
        game.setPeriod(7);
        game.setHomeRuns(4);
        game.setAwayRuns(3);
        game.setPostseason(true);
        return game;
    }

    private static ScoreTask.GameSnapshot expectedSnapshot() {
        return new ScoreTask.GameSnapshot(7, 4, 3, true);
    }
}
