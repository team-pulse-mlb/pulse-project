package com.pulse.ranking;

import com.pulse.common.config.ScoringProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

/**
 * Redis Sorted Set 기반 실시간 추천 랭킹. 전체 사용자 공용 1개만 유지하고,
 * 개인화 가산은 API 조회 시점에 적용한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    public static final String LIVE_RANK_KEY = "score:rank:live";

    private final StringRedisTemplate redisTemplate;
    private final ScoringProperties scoringProperties;

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
                    parseGameId(tuple.getValue())
                            .ifPresent(gameId -> result.put(gameId, tuple.getScore()));
                }
            }
        }
        return result;
    }

    /**
     * 현재 경기보다 설정된 점수 차 이상 높고 전환 기준 점수도 충족하는 최상위 경기 ID를 반환한다.
     * 점수는 모듈 외부로 노출하지 않는다.
     */
    public OptionalLong findSwitchCandidate(long currentGameId) {
        ZSetOperations<String, String> ranking = redisTemplate.opsForZSet();
        Double currentScore = ranking.score(LIVE_RANK_KEY, String.valueOf(currentGameId));
        if (currentScore == null) {
            return OptionalLong.empty();
        }

        ScoringProperties.Thresholds thresholds = scoringProperties.thresholds();
        double minimumCandidateScore = Math.max(
                thresholds.switchScore(),
                currentScore + thresholds.switchGap()
        );
        Set<TypedTuple<String>> candidates = ranking.reverseRangeByScoreWithScores(
                LIVE_RANK_KEY,
                minimumCandidateScore,
                Double.POSITIVE_INFINITY,
                0,
                1
        );
        if (candidates == null || candidates.isEmpty()) {
            return OptionalLong.empty();
        }

        String candidateGameId = candidates.iterator().next().getValue();
        return candidateGameId == null ? OptionalLong.empty() : parseGameId(candidateGameId);
    }

    private OptionalLong parseGameId(String member) {
        try {
            return OptionalLong.of(Long.parseLong(member));
        } catch (NumberFormatException exception) {
            log.warn("실시간 랭킹의 손상된 Redis 멤버를 건너뜁니다: member={}", member);
            return OptionalLong.empty();
        }
    }
}
