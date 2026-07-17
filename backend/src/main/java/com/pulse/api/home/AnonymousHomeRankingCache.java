package com.pulse.api.home;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
class AnonymousHomeRankingCache {

    private final HomeRankingCacheProperties properties;
    private final Clock clock;
    private final ConcurrentMap<Integer, CacheEntry> entries = new ConcurrentHashMap<>();

    AnonymousHomeRankingCache(HomeRankingCacheProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AnonymousHomeRankingCache(HomeRankingCacheProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    HomeQueryService.HomeRankingResponse get(
            int count,
            Supplier<HomeQueryService.HomeRankingResponse> loader
    ) {
        Instant now = clock.instant();
        CacheEntry entry = entries.compute(count, (ignored, current) -> {
            if (current != null && current.expiresAt().isAfter(now)) {
                return current;
            }
            return new CacheEntry(loader.get(), now.plus(properties.ttl()));
        });
        return entry.response();
    }

    private record CacheEntry(
            HomeQueryService.HomeRankingResponse response,
            Instant expiresAt
    ) {
    }
}
