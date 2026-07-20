package com.pulse.api.home;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class AnonymousHomeRankingCache {

    private final HomeRankingCacheProperties properties;
    private final Clock clock;
    private final ConcurrentMap<Integer, CacheEntry> entries = new ConcurrentHashMap<>();

    // 생성자가 둘 이상이라 Spring이 주입 대상을 특정하지 못하면 기본 생성자를 찾다 실패한다.
    // 운영 주입용 생성자를 명시해 부팅 실패를 막는다. 두 번째 생성자는 테스트에서 Clock 주입용이다.
    @Autowired
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
