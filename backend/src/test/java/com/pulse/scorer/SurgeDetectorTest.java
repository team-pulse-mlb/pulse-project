package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class SurgeDetectorTest {

    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SurgeDetector detector = new SurgeDetector(
            watchScoreRepository,
            redisTemplate,
            TestScoringProperties.version3()
    );

    private final Instant now = Instant.parse("2026-07-08T05:00:00Z");

    @Test
    void evaluate_shouldFireOnEntryAndStartCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notify:armed:100")).thenReturn(null);
        when(redisTemplate.hasKey("notify:cooldown:global")).thenReturn(false);

        boolean fired = detector.evaluate(100L, 85, now);

        assertThat(fired).isTrue();
        verify(valueOperations).set("notify:armed:100", "0");
        verify(valueOperations).set("notify:cooldown:global", String.valueOf(now.toEpochMilli()), 15, TimeUnit.MINUTES);
    }

    @Test
    void evaluate_shouldRearmBelowRearmThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:global")).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(100L, now.minusSeconds(300))).thenReturn(null);

        boolean fired = detector.evaluate(100L, 69, now);

        assertThat(fired).isFalse();
        verify(valueOperations).set("notify:armed:100", "1");
        verify(valueOperations, never()).set("notify:cooldown:global", String.valueOf(now.toEpochMilli()), 15, TimeUnit.MINUTES);
    }

    @Test
    void evaluate_shouldNotFireDuringCooldown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:global")).thenReturn(true);

        boolean fired = detector.evaluate(100L, 95, now);

        assertThat(fired).isFalse();
        verify(valueOperations, never()).set("notify:armed:100", "0");
    }

    @Test
    void evaluate_shouldFireOnRapidRiseWithinWindow() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:global")).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(100L, now.minusSeconds(300))).thenReturn(63);

        boolean fired = detector.evaluate(100L, 78, now);

        assertThat(fired).isTrue();
        verify(valueOperations).set("notify:armed:100", "0");
        verify(valueOperations).set("notify:cooldown:global", String.valueOf(now.toEpochMilli()), 15, TimeUnit.MINUTES);
    }
}
