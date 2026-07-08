package com.pulse.scorer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 라이브 계산 결과의 Redis 반영 창구. 랭킹 ZSET 갱신, 경기 HASH 캐시,
 * 재조회 신호 pub/sub를 한곳에서 처리한다. payload에는 점수·결과를 싣지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LiveSignalPublisher {

    public static final String RANKING_CHANNEL = "signal:ranking";
    private static final String GAME_CHANNEL_PREFIX = "signal:game:";
    private static final String GAME_CACHE_PREFIX = "game:";
    private static final String GAME_CACHE_SUFFIX = ":live";

    private final RankingService rankingService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

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
            Instant updatedAt
    ) {
        rankingService.updateLive(gameId, watchScore);
        cacheGameState(gameId, watchScore, baseScore, tags, inning, inningType, lastPlayOrder, lifecycleState, updatedAt);
        publishGameSignal(gameId);
        publishRankingSignal();
    }

    public void publishGameSignal(long gameId) {
        redisTemplate.convertAndSend(GAME_CHANNEL_PREFIX + gameId, String.valueOf(gameId));
    }

    public void publishRankingSignal() {
        redisTemplate.convertAndSend(RANKING_CHANNEL, "changed");
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
            Instant updatedAt
    ) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("watchScore", String.valueOf((int) Math.round(watchScore)));
        hash.put("baseScore", String.valueOf(baseScore));
        hash.put("tags", writeTags(tags));
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

    private String writeTags(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException e) {
            log.warn("태그 직렬화 실패: {}", tags, e);
            return "[]";
        }
    }

    private static String cacheKey(long gameId) {
        return GAME_CACHE_PREFIX + gameId + GAME_CACHE_SUFFIX;
    }
}
