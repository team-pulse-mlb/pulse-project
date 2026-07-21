package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 급상승(SURGE) 알림 판정과 Redis 히스테리시스 상태를 관리한다.
 * armed·cooldown·전역 발화 수는 알림 빈도 제어용 캐시라 Redis 재시작 시 초기화되어도 허용한다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
public class SurgeDetector {

    private static final String ARMED_KEY_PREFIX = "notify:armed:";
    private static final String COOLDOWN_KEY_PREFIX = "notify:cooldown:";
    private static final String GLOBAL_COUNT_KEY = "notify:surge:count:global";
    private static final String DISARMED = "0";
    private static final String ARMED = "1";
    private static final DefaultRedisScript<Long> CONFIRM_SURGE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            local count = tonumber(redis.call('GET', KEYS[3]) or '0')
            if count >= tonumber(ARGV[1]) then
                return 0
            end
            redis.call('INCR', KEYS[3])
            if redis.call('PTTL', KEYS[3]) < 0 then
                redis.call('PEXPIRE', KEYS[3], ARGV[4])
            end
            redis.call('SET', KEYS[1], '0', 'PX', ARGV[5])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);

    private final WatchScoreRepository watchScoreRepository;
    private final StringRedisTemplate redisTemplate;
    private final ScoringProperties props;
    private final Duration emergencyTtl;

    @Autowired
    public SurgeDetector(
            WatchScoreRepository watchScoreRepository,
            StringRedisTemplate redisTemplate,
            ScoringProperties props,
            @Value("${pulse.scorer.redis-emergency-ttl-ms:172800000}") long emergencyTtlMillis
    ) {
        this(
                watchScoreRepository,
                redisTemplate,
                props,
                Duration.ofMillis(emergencyTtlMillis)
        );
    }

    SurgeDetector(
            WatchScoreRepository watchScoreRepository,
            StringRedisTemplate redisTemplate,
            ScoringProperties props,
            Duration emergencyTtl
    ) {
        this.watchScoreRepository = watchScoreRepository;
        this.redisTemplate = redisTemplate;
        this.props = props;
        this.emergencyTtl = emergencyTtl;
    }

    /**
     * 커밋 후 리스너에서 후보를 판정하고 Redis 상태와 실제 발행을 확정한다.
     * 전역 상한과 경기별 상태를 Lua 한 번으로 묶어 동시 실행에서도 발행 수가 상한을 넘지 않게 한다.
     */
    public void evaluate(long gameId, int watchScore, Instant now, Runnable confirmedAction) {
        ScoringProperties.Thresholds thresholds = props.thresholds();

        if (watchScore < thresholds.alertRearmScore()) {
            redisTemplate.opsForValue().set(armedKey(gameId), ARMED, emergencyTtl);
        }

        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey(gameId)))) {
            return;
        }

        boolean armed = !DISARMED.equals(redisTemplate.opsForValue().get(armedKey(gameId)));
        boolean entryTrigger = armed && watchScore >= thresholds.alertScore();
        boolean riseTrigger = watchScore >= thresholds.alertScore()
                && risenWithinWindow(gameId, watchScore, thresholds, now);
        if (!entryTrigger && !riseTrigger) {
            return;
        }

        Long confirmed = redisTemplate.execute(
                CONFIRM_SURGE_SCRIPT,
                List.of(armedKey(gameId), cooldownKey(gameId), GLOBAL_COUNT_KEY),
                String.valueOf(thresholds.alertGlobalLimit()),
                String.valueOf(now.toEpochMilli()),
                String.valueOf(Duration.ofMinutes(thresholds.alertCooldownMinutes()).toMillis()),
                String.valueOf(Duration.ofMinutes(thresholds.alertGlobalWindowMinutes()).toMillis()),
                String.valueOf(emergencyTtl.toMillis())
        );
        if (Long.valueOf(1L).equals(confirmed)) {
            confirmedAction.run();
        }
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
