package com.pulse.poller;

import java.time.Duration;
import java.time.Instant;

public class PollerBackoff {

    private final Duration initialDelay;
    private final Duration maxDelay;
    private Duration currentDelay;
    private Instant blockedUntil = Instant.EPOCH;

    public PollerBackoff(Duration initialDelay, Duration maxDelay) {
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.currentDelay = initialDelay;
    }

    public boolean canCall(Instant now) {
        return !now.isBefore(blockedUntil);
    }

    public void recordSuccess() {
        currentDelay = initialDelay;
        blockedUntil = Instant.EPOCH;
    }

    public void recordFailure(Instant now, Duration retryAfter) {
        Duration delay = retryAfter == null || retryAfter.isNegative() || retryAfter.isZero()
                ? currentDelay
                : retryAfter;
        blockedUntil = now.plus(delay.compareTo(maxDelay) > 0 ? maxDelay : delay);
        currentDelay = doubled(currentDelay);
    }

    Instant blockedUntil() {
        return blockedUntil;
    }

    private Duration doubled(Duration delay) {
        Duration next = delay.multipliedBy(2);
        return next.compareTo(maxDelay) > 0 ? maxDelay : next;
    }
}
