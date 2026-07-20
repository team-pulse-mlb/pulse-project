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
        GameLifecycle next = calculateNext(current, sourceStatus, startTime, now);
        return preventRegression(current, next, startTime, now);
    }

    private static GameLifecycle calculateNext(
            GameLifecycle current,
            String sourceStatus,
            Instant startTime,
            Instant now
    ) {
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

    private static GameLifecycle preventRegression(
            GameLifecycle current,
            GameLifecycle next,
            Instant startTime,
            Instant now
    ) {
        if (current == GameLifecycle.DONE || current == GameLifecycle.SUSPENDED_POSTPONED) {
            return next;
        }
        if (current == GameLifecycle.FINAL && next != GameLifecycle.FINAL) {
            return current;
        }
        if (current == GameLifecycle.LIVE
                && (next == GameLifecycle.SCHEDULED
                || next == GameLifecycle.PREGAME_FAR
                || next == GameLifecycle.PREGAME_NEAR)) {
            return current;
        }
        if (current == GameLifecycle.PREGAME_NEAR
                && next == GameLifecycle.SCHEDULED
                && hasStarted(startTime, now)) {
            return current;
        }
        return next;
    }

    private static boolean hasStarted(Instant startTime, Instant now) {
        if (startTime == null) {
            return false;
        }
        Duration untilStart = Duration.between(now, startTime);
        return untilStart.isNegative() || untilStart.isZero();
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
