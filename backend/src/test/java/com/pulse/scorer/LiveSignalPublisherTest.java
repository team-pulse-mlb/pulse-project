package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.ranking.RankingService;
import com.pulse.common.transaction.AfterCommitExecutor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class LiveSignalPublisherTest {

    private final RankingService rankingService = mock(RankingService.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final LiveSignalPublisher publisher = new LiveSignalPublisher(
            rankingService,
            redisTemplate,
            new AfterCommitExecutor()
    );

    @Test
    void publishLiveUpdate_shouldUpdateRankingCacheAndSignals() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant updatedAt = Instant.parse("2026-07-08T05:00:00Z");

        publisher.publishLiveUpdate(
                100L,
                87.4,
                73,
                List.of("득점권 압박"),
                8,
                "TOP",
                42L,
                "LIVE",
                List.of(),
                updatedAt
        );

        verify(rankingService).updateLive(100L, 87.4);
        verify(hashOperations).putAll(eq("game:100:live"), anyMap());
        verify(redisTemplate).convertAndSend("signal:game:100", "100");
        verify(redisTemplate).convertAndSend("signal:ranking", "changed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishLiveUpdate_shouldCacheSpoilerFreeLiveState() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant updatedAt = Instant.parse("2026-07-08T05:00:00Z");

        publisher.publishLiveUpdate(
                100L, 87.4, 73, List.of("득점권 압박"), 8, "TOP", 42L, "LIVE", List.of(), updatedAt);

        verify(hashOperations).putAll(eq("game:100:live"), org.mockito.ArgumentMatchers.argThat(value -> {
            Map<String, String> hash = (Map<String, String>) value;
            assertThat(hash)
                    .containsEntry("watchScore", "87")
                    .containsEntry("baseScore", "73")
                    .containsEntry("latestTag", "득점권 압박")
                    .containsEntry("latestTagActivatedAt", updatedAt.toString())
                    .containsEntry("activeTagSignature", "득점권 압박")
                    .containsEntry("lifecycleState", "LIVE")
                    .containsEntry("updatedAt", updatedAt.toString())
                    .containsEntry("inning", "8")
                    .containsEntry("inningType", "TOP")
                    .containsEntry("lastPlayOrder", "42");
            return true;
        }));
    }

    @Test
    @DisplayName("Redis 서명 미스 시 직전 점수 태그를 기준으로 새 태그를 선택한다")
    @SuppressWarnings("unchecked")
    void publishLiveUpdateUsesFallbackTagsOnRedisMiss() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        Instant updatedAt = Instant.parse("2026-07-08T05:00:00Z");

        publisher.publishLiveUpdate(
                100L,
                87.4,
                73,
                List.of("접전 흐름", "풀카운트 승부"),
                8,
                "TOP",
                42L,
                "LIVE",
                List.of("접전 흐름"),
                updatedAt
        );

        verify(hashOperations).putAll(eq("game:100:live"), org.mockito.ArgumentMatchers.argThat(value -> {
            Map<String, String> hash = (Map<String, String>) value;
            assertThat(hash).containsEntry("latestTag", "풀카운트 승부");
            return true;
        }));
    }

    @Test
    @DisplayName("빈 Redis 서명은 fallback과 무관하게 이전 태그가 없는 상태로 처리한다")
    @SuppressWarnings("unchecked")
    void publishLiveUpdateKeepsEmptyRedisSignatureBehavior() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("game:100:live", "activeTagSignature")).thenReturn("");
        Instant updatedAt = Instant.parse("2026-07-08T05:00:00Z");

        publisher.publishLiveUpdate(
                100L,
                87.4,
                73,
                List.of("접전 흐름"),
                8,
                "TOP",
                42L,
                "LIVE",
                List.of("접전 흐름"),
                updatedAt
        );

        verify(hashOperations).putAll(eq("game:100:live"), org.mockito.ArgumentMatchers.argThat(value -> {
            Map<String, String> hash = (Map<String, String>) value;
            assertThat(hash)
                    .containsEntry("latestTag", "접전 흐름")
                    .containsEntry("latestTagActivatedAt", updatedAt.toString());
            return true;
        }));
    }
}
