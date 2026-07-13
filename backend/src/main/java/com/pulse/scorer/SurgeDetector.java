package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 급상승(SURGE) 알림 판정. 점수가 임계값 이상이면서 진입하거나 급상승한 경우에만 발화하며,
 * 경기별 쿨다운과 전역 윈도 내 최대 발화 횟수를 Redis 상태로 관리한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SurgeDetector {

    private static final String ARMED_KEY_PREFIX = "notify:armed:";
    private static final String COOLDOWN_KEY_PREFIX = "notify:cooldown:";
    private static final String GLOBAL_COUNT_KEY = "notify:surge:count:global";
    private static final String DISARMED = "0";
    private static final String ARMED = "1";

    private final WatchScoreRepository watchScoreRepository;
    private final StringRedisTemplate redisTemplate;
    private final ScoringProperties props;

    /**
     * 이번 사이클에서 SURGE를 발화할지 판정한다. 발화 시 재무장을 해제하고 경기별 쿨다운을 설정하며,
     * 전역 윈도 내 발화 횟수를 증가시킨다.
     * 점수가 재무장 임계 아래로 내려가면 다음 진입을 위해 재무장한다.
     */
    public boolean evaluate(long gameId, int watchScore, Instant now) {
        ScoringProperties.Thresholds thresholds = props.thresholds();

        if (watchScore < thresholds.alertRearmScore()) {
            redisTemplate.opsForValue().set(armedKey(gameId), ARMED);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(gameId)))) {
            return false;
        }

        boolean armed = !DISARMED.equals(redisTemplate.opsForValue().get(armedKey(gameId)));
        boolean entryTrigger = armed && watchScore >= thresholds.alertScore();
        boolean riseTrigger = watchScore >= thresholds.alertScore()
                && risenWithinWindow(gameId, watchScore, thresholds, now);
        if (!entryTrigger && !riseTrigger) {
            return false;
        }

        String globalCountValue = redisTemplate.opsForValue().get(GLOBAL_COUNT_KEY);
        long globalCount = globalCountValue == null ? 0 : Long.parseLong(globalCountValue);
        if (globalCount >= thresholds.alertGlobalLimit()) {
            return false;
        }

        redisTemplate.opsForValue().set(armedKey(gameId), DISARMED);
        redisTemplate.opsForValue().set(
                cooldownKey(gameId),
                String.valueOf(now.toEpochMilli()),
                thresholds.alertCooldownMinutes(),
                TimeUnit.MINUTES
        );
        Long incrementedCount = redisTemplate.opsForValue().increment(GLOBAL_COUNT_KEY);
        if (Long.valueOf(1L).equals(incrementedCount)) {
            redisTemplate.expire(
                    GLOBAL_COUNT_KEY,
                    thresholds.alertGlobalWindowMinutes(),
                    TimeUnit.MINUTES
            );
        }
        return true;
    }

    private boolean risenWithinWindow(long gameId, int watchScore, ScoringProperties.Thresholds thresholds, Instant now) {
        Instant since = now.minus(Duration.ofMinutes(thresholds.alertRiseWindowMinutes()));
        Integer minInWindow = watchScoreRepository.findMinWatchScoreSince(gameId, since);
        if (minInWindow == null) {
            return false;
        }
        return watchScore - minInWindow >= thresholds.alertRiseScore();
    }

    private static String armedKey(long gameId) {
        return ARMED_KEY_PREFIX + gameId;
    }

    private static String cooldownKey(long gameId) {
        return COOLDOWN_KEY_PREFIX + gameId;
    }
}
