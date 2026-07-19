package com.pulse.api.home;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnonymousHomeRankingCacheTest {

    @Test
    @DisplayName("익명 랭킹 요청은 짧은 TTL 동안 한 번만 조회한다")
    void get_shouldReuseResponseUntilTtlExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        AnonymousHomeRankingCache cache = new AnonymousHomeRankingCache(
                new HomeRankingCacheProperties(Duration.ofSeconds(3)), clock);
        AtomicInteger loads = new AtomicInteger();

        HomeRankingResponse first = cache.get(5, () -> response(loads.incrementAndGet()));
        HomeRankingResponse cached = cache.get(5, () -> response(loads.incrementAndGet()));
        clock.advance(Duration.ofSeconds(3));
        HomeRankingResponse refreshed = cache.get(5, () -> response(loads.incrementAndGet()));

        assertThat(cached).isSameAs(first);
        assertThat(refreshed).isNotSameAs(first);
        assertThat(loads).hasValue(2);
    }

    private static HomeRankingResponse response(int sequence) {
        return new HomeRankingResponse(
                Instant.ofEpochSecond(sequence), List.of(), List.of(), List.of());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
