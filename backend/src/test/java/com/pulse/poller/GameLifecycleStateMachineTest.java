package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.domain.Game;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GameLifecycleStateMachineTest {

    private final GameLifecycleStateMachine stateMachine = new GameLifecycleStateMachine();
    private final Instant now = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void transition_shouldEnterLiveFromAnyState() {
        GameLifecycle next = stateMachine.transition(
                GameLifecycle.PREGAME_NEAR.name(),
                Game.STATUS_IN_PROGRESS,
                now.plusSeconds(60),
                now
        );

        assertThat(next).isEqualTo(GameLifecycle.LIVE);
    }

    @Test
    void transition_shouldEnterPregameNearWithinSixHours() {
        GameLifecycle next = stateMachine.transition(
                GameLifecycle.SCHEDULED.name(),
                Game.STATUS_SCHEDULED,
                now.plusSeconds(6 * 60 * 60),
                now
        );

        assertThat(next).isEqualTo(GameLifecycle.PREGAME_NEAR);
    }

    @Test
    void transition_shouldEnterSuspendedPostponedWhenLiveGameIsPostponed() {
        GameLifecycle next = stateMachine.transition(
                GameLifecycle.LIVE.name(),
                Game.STATUS_POSTPONED,
                now.minusSeconds(60),
                now
        );

        assertThat(next).isEqualTo(GameLifecycle.SUSPENDED_POSTPONED);
    }

    @Test
    void transition_shouldEnterDoneWhenScheduledGameIsCanceled() {
        GameLifecycle next = stateMachine.transition(
                GameLifecycle.SCHEDULED.name(),
                Game.STATUS_CANCELED,
                now.plusSeconds(3600),
                now
        );

        assertThat(next).isEqualTo(GameLifecycle.DONE);
    }
}
