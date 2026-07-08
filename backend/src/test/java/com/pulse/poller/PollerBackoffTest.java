package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PollerBackoffTest {

    @Test
    void recordFailure_shouldBlockUntilRetryAfter() {
        PollerBackoff backoff = new PollerBackoff(Duration.ofSeconds(10), Duration.ofMinutes(5));
        Instant now = Instant.parse("2026-07-08T00:00:00Z");

        backoff.recordFailure(now, Duration.ofSeconds(30));

        assertThat(backoff.canCall(now.plusSeconds(29))).isFalse();
        assertThat(backoff.canCall(now.plusSeconds(30))).isTrue();
    }

    @Test
    void recordSuccess_shouldClearBackoff() {
        PollerBackoff backoff = new PollerBackoff(Duration.ofSeconds(10), Duration.ofMinutes(5));
        Instant now = Instant.parse("2026-07-08T00:00:00Z");

        backoff.recordFailure(now, null);
        backoff.recordSuccess();

        assertThat(backoff.canCall(now)).isTrue();
    }
}
