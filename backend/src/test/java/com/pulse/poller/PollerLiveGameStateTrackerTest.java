package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PollerLiveGameStateTrackerTest {

    private final PollerLiveGameStateTracker stateTracker = new PollerLiveGameStateTracker(properties());
    private final Instant now = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void quiet_shouldBecomeTrueAtQuietThreshold() {
        long gameId = 100L;

        assertThat(stateTracker.quiet(gameId, now)).isFalse();
        assertThat(stateTracker.quiet(gameId, now.plus(Duration.ofMinutes(9)).plusSeconds(59))).isFalse();
        assertThat(stateTracker.quiet(gameId, now.plus(Duration.ofMinutes(10)))).isTrue();
    }

    @Test
    void heartbeatDue_shouldBecomeTrueAtHeartbeatInterval() {
        long gameId = 100L;
        stateTracker.markTaskPublished(gameId, now);

        assertThat(stateTracker.heartbeatDue(gameId, now.plusSeconds(74))).isFalse();
        assertThat(stateTracker.heartbeatDue(gameId, now.plusSeconds(75))).isTrue();
    }

    @Test
    void incrementEmptyFetchStreak_shouldCountConsecutiveEmptyFetches() {
        long gameId = 100L;

        assertThat(stateTracker.incrementEmptyFetchStreak(gameId)).isEqualTo(1);
        assertThat(stateTracker.incrementEmptyFetchStreak(gameId)).isEqualTo(2);

        stateTracker.resetEmptyFetchStreak(gameId);

        assertThat(stateTracker.incrementEmptyFetchStreak(gameId)).isEqualTo(1);
    }

    @Test
    void clear_shouldRemoveAllGameState() {
        long gameId = 100L;
        stateTracker.markTaskPublished(gameId, now);
        stateTracker.markNewPlays(gameId, now);
        stateTracker.scheduleQuietPoll(gameId, now);
        stateTracker.incrementEmptyFetchStreak(gameId);
        stateTracker.incrementRecoveryStepBack(gameId);

        stateTracker.clear(gameId);

        assertThat(stateTracker.heartbeatDue(gameId, now)).isTrue();
        assertThat(stateTracker.quietPollScheduledAfter(gameId, now)).isFalse();
        assertThat(stateTracker.incrementEmptyFetchStreak(gameId)).isEqualTo(1);
        assertThat(stateTracker.incrementRecoveryStepBack(gameId)).isEqualTo(1);
        assertThat(stateTracker.quiet(gameId, now)).isFalse();
    }

    private static PollerProperties properties() {
        return new PollerProperties(
                true,
                null,
                Duration.ofSeconds(75),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                null,
                null,
                null,
                0,
                0,
                9,
                5,
                null,
                null,
                1000,
                null,
                null,
                null,
                10,
                null
        );
    }
}
