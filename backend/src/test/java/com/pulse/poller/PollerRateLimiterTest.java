package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PollerRateLimiterTest {

    @Test
    void tryAcquire_shouldCapCallsPerSecondWindow() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        PollerRateLimiter limiter = new PollerRateLimiter(2, Clock.systemUTC());

        assertThat(limiter.tryAcquire(now)).isTrue();
        assertThat(limiter.tryAcquire(now)).isTrue();
        assertThat(limiter.tryAcquire(now)).isFalse();
        assertThat(limiter.tryAcquire(now.plusMillis(999))).isFalse();
        assertThat(limiter.tryAcquire(now.plusSeconds(1))).isTrue();
    }
}
