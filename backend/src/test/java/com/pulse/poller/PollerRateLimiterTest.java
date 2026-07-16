package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("토큰 대기 중 인터럽트되면 호출을 진행하지 않고 예외를 전파한다")
    void acquire_shouldFailWhenInterruptedWhileWaiting() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        PollerRateLimiter limiter = new PollerRateLimiter(1, Clock.fixed(now, ZoneOffset.UTC));
        assertThat(limiter.tryAcquire(now)).isTrue();

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(limiter::acquire)
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
