package com.pulse.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

@SuppressWarnings("unchecked")
class RankingServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> ranking = mock(ZSetOperations.class);
    private final RankingService rankingService = new RankingService(redisTemplate, scoringProperties());

    @Test
    @DisplayName("실시간 랭킹에서 손상된 멤버를 건너뛰고 정상 경기만 반환한다")
    void topLiveSkipsDamagedMember() {
        TypedTuple<String> validMember = mock(TypedTuple.class);
        TypedTuple<String> damagedMember = mock(TypedTuple.class);
        Set<TypedTuple<String>> members = new LinkedHashSet<>();
        members.add(validMember);
        members.add(damagedMember);
        when(redisTemplate.opsForZSet()).thenReturn(ranking);
        when(ranking.reverseRangeWithScores(RankingService.LIVE_RANK_KEY, 0, 9))
                .thenReturn(members);
        when(validMember.getValue()).thenReturn("100");
        when(validMember.getScore()).thenReturn(90.0);
        when(damagedMember.getValue()).thenReturn("damaged");
        when(damagedMember.getScore()).thenReturn(80.0);

        assertThat(rankingService.topLive(10)).containsExactlyEntriesOf(Map.of(100L, 90.0));
    }

    @Test
    @DisplayName("현재 경기보다 20점 이상 높고 70점 이상인 최상위 경기를 반환한다")
    void findSwitchCandidateReturnsHighestEligibleGame() {
        TypedTuple<String> candidate = mock(TypedTuple.class);
        when(redisTemplate.opsForZSet()).thenReturn(ranking);
        when(ranking.score(RankingService.LIVE_RANK_KEY, "100")).thenReturn(55.0);
        when(ranking.reverseRangeByScoreWithScores(
                RankingService.LIVE_RANK_KEY, 75.0, Double.POSITIVE_INFINITY, 0, 1))
                .thenReturn(Set.of(candidate));
        when(candidate.getValue()).thenReturn("200");

        assertThat(rankingService.findSwitchCandidate(100L)).hasValue(200L);
    }

    @Test
    @DisplayName("현재 경기 점수가 기준보다 낮으면 전환 최저 점수 70점을 적용한다")
    void findSwitchCandidateAppliesAbsoluteMinimumScore() {
        when(redisTemplate.opsForZSet()).thenReturn(ranking);
        when(ranking.score(RankingService.LIVE_RANK_KEY, "100")).thenReturn(40.0);
        when(ranking.reverseRangeByScoreWithScores(
                RankingService.LIVE_RANK_KEY, 70.0, Double.POSITIVE_INFINITY, 0, 1))
                .thenReturn(Set.of());

        assertThat(rankingService.findSwitchCandidate(100L)).isEmpty();
        verify(ranking).reverseRangeByScoreWithScores(
                RankingService.LIVE_RANK_KEY, 70.0, Double.POSITIVE_INFINITY, 0, 1);
    }

    @Test
    @DisplayName("현재 경기가 실시간 랭킹에 없으면 전환 후보를 조회하지 않는다")
    void findSwitchCandidateReturnsEmptyWhenCurrentGameIsMissing() {
        when(redisTemplate.opsForZSet()).thenReturn(ranking);
        when(ranking.score(RankingService.LIVE_RANK_KEY, "100")).thenReturn(null);

        assertThat(rankingService.findSwitchCandidate(100L)).isEmpty();
    }

    @Test
    @DisplayName("전환 후보의 Redis 멤버가 손상됐으면 빈 결과를 반환한다")
    void findSwitchCandidateReturnsEmptyWhenCandidateMemberIsDamaged() {
        TypedTuple<String> damagedCandidate = mock(TypedTuple.class);
        when(redisTemplate.opsForZSet()).thenReturn(ranking);
        when(ranking.score(RankingService.LIVE_RANK_KEY, "100")).thenReturn(55.0);
        when(ranking.reverseRangeByScoreWithScores(
                RankingService.LIVE_RANK_KEY, 75.0, Double.POSITIVE_INFINITY, 0, 1))
                .thenReturn(Set.of(damagedCandidate));
        when(damagedCandidate.getValue()).thenReturn("damaged");

        assertThat(rankingService.findSwitchCandidate(100L)).isEmpty();
    }

    private static ScoringProperties scoringProperties() {
        return new ScoringProperties(
                5, null, null, null, null, null, null, null, null, null,
                10, new ScoringProperties.Personalization(10, 5, 15), null, null, null,
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 3, 15, 70, 20),
                null
        );
    }
}
