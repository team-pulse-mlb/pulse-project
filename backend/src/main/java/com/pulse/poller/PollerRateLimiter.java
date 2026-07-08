package com.pulse.poller;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * 외부 API 호출량 상한. 초 단위 창으로 호출 수를 제한하고,
 * 토큰이 없으면 다음 창까지 대기해 사이클 내 버스트를 스태거한다.
 */
@Component
public class PollerRateLimiter {

    private static final long WAIT_SLICE_MILLIS = 50;

    private final int permitsPerSecond;
    private final Clock clock;

    private long windowEpochSecond = Long.MIN_VALUE;
    private int usedInWindow;

    public PollerRateLimiter(PollerProperties properties) {
        this(properties.rateLimitPerSecond(), Clock.systemUTC());
    }

    PollerRateLimiter(int permitsPerSecond, Clock clock) {
        this.permitsPerSecond = permitsPerSecond;
        this.clock = clock;
    }

    /** 토큰을 얻을 때까지 대기한다. */
    public void acquire() {
        while (!tryAcquire(clock.instant())) {
            try {
                Thread.sleep(WAIT_SLICE_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    synchronized boolean tryAcquire(Instant now) {
        long second = now.getEpochSecond();
        if (second != windowEpochSecond) {
            windowEpochSecond = second;
            usedInWindow = 0;
        }
        if (usedInWindow >= permitsPerSecond) {
            return false;
        }
        usedInWindow++;
        return true;
    }
}
