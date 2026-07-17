package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.domain.Game;
import org.junit.jupiter.api.Test;

class GameUpsertResultTest {

    @Test
    void enteredLive_shouldBeTrueOnlyOnLiveTransition() {
        assertThat(result(GameLifecycle.SCHEDULED, GameLifecycle.LIVE, false).enteredLive()).isTrue();
        assertThat(result(GameLifecycle.LIVE, GameLifecycle.LIVE, true).enteredLive()).isFalse();
    }

    @Test
    void enteredTerminalState_shouldRequireOnlyLifecycleTransition() {
        assertThat(result(GameLifecycle.LIVE, GameLifecycle.FINAL, true).enteredTerminalState()).isTrue();
        assertThat(result(GameLifecycle.FINAL, GameLifecycle.FINAL, false).enteredTerminalState()).isFalse();
        assertThat(result(GameLifecycle.SCHEDULED, GameLifecycle.DONE, false).enteredTerminalState()).isTrue();
    }

    private static PollerGameWriter.GameUpsertResult result(
            GameLifecycle previous,
            GameLifecycle current,
            boolean wasLive
    ) {
        Game game = new Game();
        game.setId(1L);
        game.setLifecycleState(current.name());
        return new PollerGameWriter.GameUpsertResult(game, previous.name(), current.name(), wasLive);
    }
}
