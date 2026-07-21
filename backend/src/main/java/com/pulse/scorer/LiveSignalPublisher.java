package com.pulse.scorer;

import com.pulse.common.message.RedisSignalChannels;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 라이브 계산 결과의 Redis 반영 창구. 랭킹 ZSET 갱신, 경기 HASH 캐시,
 * 재조회 신호 pub/sub를 한곳에서 처리한다. payload에는 점수·결과를 싣지 않는다.
 *
 * <p>메서드는 모두 동기 실행이다. 라이브 계산 경로의 Redis 반영은 커밋 후
 * {@link LiveRedisProjectionCommitListener}가 호출하므로 자체적으로 커밋 위상을
 * 지연시키지 않는다. 종료 경기 정리 경로는 본 트랜잭션에서 직접 호출하며 DB 파생
 * 쓰기가 없어 커밋 선후관계에 의존하지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class LiveSignalPublisher {

    private static final String GAME_CACHE_PREFIX = "game:";
    private static final String GAME_CACHE_SUFFIX = ":live";

    private final RankingService rankingService;
    private final StringRedisTemplate redisTemplate;
    private final LatestTagSelector latestTagSelector;

    /** 랭킹 ZSET·경기 HASH 캐시를 갱신하고 재조회 신호 2종을 발행한다. */
    public void publishLiveUpdate(
            long gameId,
            double watchScore,
            int baseScore,
            List<String> tags,
            Integer inning,
            String inningType,
            Long lastPlayOrder,
            String lifecycleState,
            List<String> fallbackPreviousTags,
            Instant updatedAt
    ) {
        rankingService.updateLive(gameId, watchScore);
        PulseMetrics.increment("pulse.scorer.ranking.updated");
        cacheGameState(
                gameId,
                watchScore,
                baseScore,
                tags,
                inning,
                inningType,
                lastPlayOrder,
                lifecycleState,
                fallbackPreviousTags,
                updatedAt
        );
        publishGameSignalNow(gameId);
        publishRankingSignalNow();
    }

    public void publishGameSignal(long gameId) {
        publishGameSignalNow(gameId);
    }

    public void publishRankingSignal() {
        publishRankingSignalNow();
    }

    private void publishGameSignalNow(long gameId) {
        redisTemplate.convertAndSend(RedisSignalChannels.gameChannel(gameId), String.valueOf(gameId));
    }

    private void publishRankingSignalNow() {
        redisTemplate.convertAndSend(RedisSignalChannels.RANKING, "changed");
    }

    private void cacheGameState(
            long gameId,
            double watchScore,
            int baseScore,
            List<String> tags,
            Integer inning,
            String inningType,
            Long lastPlayOrder,
            String lifecycleState,
            List<String> fallbackPreviousTags,
            Instant updatedAt
    ) {
        LatestTagState latestTagState = latestTagState(gameId, tags, fallbackPreviousTags, updatedAt);
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("watchScore", String.valueOf((int) Math.round(watchScore)));
        hash.put("baseScore", String.valueOf(baseScore));
        hash.put("latestTag", latestTagState.latestTag());
        hash.put("latestTagActivatedAt", latestTagState.activatedAt());
        hash.put("activeTagSignature", tagSignature(tags));
        hash.put("lifecycleState", lifecycleState == null ? "" : lifecycleState);
        hash.put("updatedAt", updatedAt.toString());
        if (inning != null) {
            hash.put("inning", String.valueOf(inning));
        }
        if (inningType != null) {
            hash.put("inningType", inningType);
        }
        if (lastPlayOrder != null) {
            hash.put("lastPlayOrder", String.valueOf(lastPlayOrder));
        }
        redisTemplate.opsForHash().putAll(cacheKey(gameId), hash);
    }

    public void evictGameCache(long gameId) {
        redisTemplate.delete(cacheKey(gameId));
    }

    public void removeLiveGame(long gameId) {
        rankingService.removeLive(gameId);
    }

    private LatestTagState latestTagState(
            long gameId,
            List<String> tags,
            List<String> fallbackPreviousTags,
            Instant updatedAt
    ) {
        List<String> current = tags == null ? List.of() : tags;
        String key = cacheKey(gameId);
        String previousSignature = valueOf(redisTemplate.opsForHash().get(key, "activeTagSignature"));
        String previousLatestTag = valueOf(redisTemplate.opsForHash().get(key, "latestTag"));
        String previousActivatedAt = valueOf(redisTemplate.opsForHash().get(key, "latestTagActivatedAt"));

        if (current.isEmpty()) {
            return new LatestTagState("", "");
        }

        List<String> previousTags;
        if (previousSignature == null) {
            previousTags = fallbackPreviousTags == null ? List.of() : fallbackPreviousTags;
        } else if (previousSignature.isBlank()) {
            previousTags = List.of();
        } else {
            previousTags = List.of(previousSignature.split("\\|", -1));
        }
        LatestTagSelector.Selection selection =
                latestTagSelector.select(current, previousTags, previousLatestTag);
        if (selection.newlyActivated()) {
            return new LatestTagState(selection.tag(), updatedAt.toString());
        }
        if (previousLatestTag != null && selection.tag().equals(previousLatestTag)) {
            String activatedAt = previousActivatedAt == null ? updatedAt.toString() : previousActivatedAt;
            return new LatestTagState(selection.tag(), activatedAt);
        }
        return new LatestTagState(selection.tag(), updatedAt.toString());
    }

    private static String tagSignature(List<String> tags) {
        return String.join("|", tags == null ? List.of() : tags);
    }

    private static String valueOf(Object value) {
        return value == null ? null : value.toString();
    }

    private static String cacheKey(long gameId) {
        return GAME_CACHE_PREFIX + gameId + GAME_CACHE_SUFFIX;
    }

    private record LatestTagState(String latestTag, String activatedAt) {
    }
}
