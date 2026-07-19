package com.pulse.poller;

import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class GameLifecycleStateMachine {

    private static final Duration PREGAME_FAR_WINDOW = Duration.ofHours(36);
    private static final Duration PREGAME_NEAR_WINDOW = Duration.ofHours(6);

    public GameLifecycle transition(String currentState, String sourceStatus, Instant startTime, Instant now) {
        GameLifecycle current = parse(currentState);
        if (Game.STATUS_IN_PROGRESS.equals(sourceStatus)) {
            return GameLifecycle.LIVE;
        }
        if (sourceStatus != null && sourceStatus.startsWith(Game.STATUS_FINAL)) {
            return GameLifecycle.FINAL;
        }
        if (Game.STATUS_CANCELED.equals(sourceStatus)) {
            return GameLifecycle.DONE;
        }
        if (Game.STATUS_POSTPONED.equals(sourceStatus)) {
            return current == GameLifecycle.LIVE ? GameLifecycle.SUSPENDED_POSTPONED : GameLifecycle.DONE;
        }
        if (startTime == null) {
            return GameLifecycle.SCHEDULED;
        }

        Duration untilStart = Duration.between(now, startTime);
        if (!untilStart.isNegative() && untilStart.compareTo(PREGAME_NEAR_WINDOW) <= 0) {
            return GameLifecycle.PREGAME_NEAR;
        }
        if (!untilStart.isNegative() && untilStart.compareTo(PREGAME_FAR_WINDOW) <= 0) {
            return GameLifecycle.PREGAME_FAR;
        }
        return GameLifecycle.SCHEDULED;
    }

    private static GameLifecycle parse(String state) {
        if (state == null || state.isBlank()) {
            return GameLifecycle.SCHEDULED;
        }
        try {
            return GameLifecycle.valueOf(state);
        } catch (IllegalArgumentException e) {
            return GameLifecycle.SCHEDULED;
        }
    }
}
