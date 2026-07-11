package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
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
            TestScoringProperties.version4()
    );

    private final Instant now = Instant.parse("2026-07-08T05:00:00Z");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void evaluate_shouldFireOnEntryAndRecordLimits() {
        when(valueOperations.get("notify:armed:100")).thenReturn(null);
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(valueOperations.get("notify:surge:count:global")).thenReturn("1");
        when(valueOperations.increment("notify:surge:count:global")).thenReturn(2L);

        boolean fired = detector.evaluate(100L, 85, now);

        assertThat(fired).isTrue();
        verify(valueOperations).set("notify:armed:100", "0");
        verify(valueOperations).set("notify:cooldown:100", String.valueOf(now.toEpochMilli()), 15, TimeUnit.MINUTES);
        verify(valueOperations).increment("notify:surge:count:global");
    }

    @Test
    void evaluate_shouldNotFireOnRapidRiseBelowAlertScore() {
        when(valueOperations.get("notify:armed:100")).thenReturn("0");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(100L, now.minusSeconds(300))).thenReturn(63);

        boolean fired = detector.evaluate(100L, 78, now);

        assertThat(fired).isFalse();
        verify(watchScoreRepository, never()).findMinWatchScoreSince(100L, now.minusSeconds(300));
        verify(valueOperations, never()).set("notify:armed:100", "0");
    }

    @Test
    void evaluate_shouldFireOnRapidRiseAboveAlertScoreWhileDisarmed() {
        when(valueOperations.get("notify:armed:100")).thenReturn("0");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(100L, now.minusSeconds(300))).thenReturn(70);
        when(valueOperations.get("notify:surge:count:global")).thenReturn("0");
        when(valueOperations.increment("notify:surge:count:global")).thenReturn(1L);

        boolean fired = detector.evaluate(100L, 90, now);

        assertThat(fired).isTrue();
        verify(valueOperations).set("notify:armed:100", "0");
        verify(valueOperations).set("notify:cooldown:100", String.valueOf(now.toEpochMilli()), 15, TimeUnit.MINUTES);
    }

    @Test
    void evaluate_shouldNotFireDuringGameCooldown() {
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(true);

        boolean fired = detector.evaluate(100L, 95, now);

        assertThat(fired).isFalse();
        verify(valueOperations, never()).set("notify:armed:100", "0");
        verify(valueOperations, never()).increment("notify:surge:count:global");
    }

    @Test
    void evaluate_shouldPreserveOpportunityWhenGlobalLimitReached() {
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(valueOperations.get("notify:surge:count:global")).thenReturn("3");

        boolean fired = detector.evaluate(100L, 85, now);

        assertThat(fired).isFalse();
        verify(valueOperations, never()).set("notify:armed:100", "0");
        verify(valueOperations, never()).set(
                "notify:cooldown:100",
                String.valueOf(now.toEpochMilli()),
                15,
                TimeUnit.MINUTES
        );
        verify(valueOperations, never()).increment("notify:surge:count:global");
    }

    @Test
    void evaluate_shouldRearmBelowRearmThreshold() {
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);

        boolean fired = detector.evaluate(100L, 69, now);

        assertThat(fired).isFalse();
        verify(valueOperations).set("notify:armed:100", "1");
        verify(valueOperations, never()).set("notify:armed:100", "0");
    }

    @Test
    void evaluate_shouldSetGlobalWindowOnFirstIncrement() {
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(valueOperations.get("notify:surge:count:global")).thenReturn(null);
        when(valueOperations.increment("notify:surge:count:global")).thenReturn(1L);

        boolean fired = detector.evaluate(100L, 85, now);

        assertThat(fired).isTrue();
        verify(redisTemplate).expire("notify:surge:count:global", 15, TimeUnit.MINUTES);
    }
}
