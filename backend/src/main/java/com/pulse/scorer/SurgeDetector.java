package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 급상승(SURGE) 알림 판정. 히스테리시스(진입 85 / 재무장 70)와
 * 전역 쿨다운(15분), 급등(최근 5분 +15)을 Redis 상태로 관리한다.
 */
@Component
@RequiredArgsConstructor
public class SurgeDetector {

    private static final String ARMED_KEY_PREFIX = "notify:armed:";
    private static final String COOLDOWN_KEY = "notify:cooldown:global";
    private static final String DISARMED = "0";
    private static final String ARMED = "1";

    private final WatchScoreRepository watchScoreRepository;
    private final StringRedisTemplate redisTemplate;
    private final ScoringProperties props;

    /**
     * 이번 사이클에서 SURGE를 발화할지 판정한다. 발화 시 재무장 해제와 전역 쿨다운을 설정한다.
     * 점수가 재무장 임계 아래로 내려가면 다음 진입을 위해 재무장한다.
     */
    public boolean evaluate(long gameId, int watchScore, Instant now) {
        ScoringProperties.Thresholds thresholds = props.thresholds();

        if (watchScore < thresholds.alertRearmScore()) {
            redisTemplate.opsForValue().set(armedKey(gameId), ARMED);
        }

        boolean armed = !DISARMED.equals(redisTemplate.opsForValue().get(armedKey(gameId)));
        boolean cooldownActive = Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY));
        if (!armed || cooldownActive) {
            return false;
        }

        boolean entryTrigger = watchScore >= thresholds.alertScore();
        boolean riseTrigger = risenWithinWindow(gameId, watchScore, thresholds, now);
        if (!entryTrigger && !riseTrigger) {
            return false;
        }

        redisTemplate.opsForValue().set(armedKey(gameId), DISARMED);
        redisTemplate.opsForValue().set(
                COOLDOWN_KEY,
                String.valueOf(now.toEpochMilli()),
                thresholds.alertCooldownMinutes(),
                TimeUnit.MINUTES
        );
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
}
