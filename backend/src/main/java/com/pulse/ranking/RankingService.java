package com.pulse.ranking;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Redis Sorted Set 기반 실시간 추천 랭킹. 전체 사용자 공용 1개만 유지하고,
 * 개인화 가산은 API 조회 시점에 적용한다.
 */
@Service
@RequiredArgsConstructor
public class RankingService {

    public static final String LIVE_RANK_KEY = "score:rank:live";

    private final StringRedisTemplate redisTemplate;

    public void updateLive(long gameId, double watchScore) {
        redisTemplate.opsForZSet().add(LIVE_RANK_KEY, String.valueOf(gameId), watchScore);
    }

    public void removeLive(long gameId) {
        redisTemplate.opsForZSet().remove(LIVE_RANK_KEY, String.valueOf(gameId));
    }

    public Map<Long, Double> topLive(int count) {
        Set<TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(LIVE_RANK_KEY, 0, count - 1L);
        Map<Long, Double> result = new LinkedHashMap<>();
        if (tuples != null) {
            for (TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() != null && tuple.getScore() != null) {
                    result.put(Long.parseLong(tuple.getValue()), tuple.getScore());
                }
            }
        }
        return result;
    }
}
