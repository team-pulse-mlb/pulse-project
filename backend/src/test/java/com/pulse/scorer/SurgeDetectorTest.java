package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.WatchScoreRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SurgeDetectorTest {

    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final SurgeDetector detector = new SurgeDetector(
            watchScoreRepository,
            redisTemplate,
            TestScoringProperties.version5(),
            new AfterCommitExecutor(),
            Duration.ofHours(48)
    );

    private final Instant now = Instant.parse("2026-07-08T05:00:00Z");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void cleanUpTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void evaluate_shouldConfirmStateAndFireAfterCandidateAccepted() {
        when(valueOperations.get("notify:armed:100")).thenReturn(null);
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        confirmNextCandidate(1L);
        AtomicBoolean fired = new AtomicBoolean();

        detector.evaluate(100L, 85, now, () -> fired.set(true));

        assertThat(fired).isTrue();
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void evaluate_shouldNotChangeRedisOrFireWhenTransactionRollsBack() {
        when(valueOperations.get("notify:armed:100")).thenReturn(null);
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        AtomicBoolean fired = new AtomicBoolean();
        beginTransaction();

        detector.evaluate(100L, 85, now, () -> fired.set(true));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(fired).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void evaluate_shouldUseOneAtomicCommandForGlobalLimitAndStateChange() {
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        confirmNextCandidate(0L);
        AtomicBoolean fired = new AtomicBoolean();

        detector.evaluate(100L, 85, now, () -> fired.set(true));

        assertThat(fired).isFalse();
        verify(valueOperations, never()).get("notify:surge:count:global");
        verify(valueOperations, never()).increment("notify:surge:count:global");
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void evaluate_shouldNotFireOnRapidRiseBelowAlertScore() {
        when(valueOperations.get("notify:armed:100")).thenReturn("0");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        AtomicBoolean fired = new AtomicBoolean();

        detector.evaluate(100L, 78, now, () -> fired.set(true));

        assertThat(fired).isFalse();
        verify(watchScoreRepository, never()).findMinWatchScoreSince(100L, now.minusSeconds(300));
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void evaluate_shouldFireOnRapidRiseAboveAlertScoreWhileDisarmed() {
        when(valueOperations.get("notify:armed:100")).thenReturn("0");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        when(watchScoreRepository.findMinWatchScoreSince(100L, now.minusSeconds(300))).thenReturn(70);
        confirmNextCandidate(1L);
        AtomicBoolean fired = new AtomicBoolean();

        detector.evaluate(100L, 90, now, () -> fired.set(true));

        assertThat(fired).isTrue();
    }

    @Test
    void evaluate_shouldNotFireDuringGameCooldown() {
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(true);
        AtomicBoolean fired = new AtomicBoolean();

        detector.evaluate(100L, 95, now, () -> fired.set(true));

        assertThat(fired).isFalse();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void evaluate_shouldRearmOnlyAfterCommitWithEmergencyTtl() {
        when(valueOperations.get("notify:armed:100")).thenReturn("1");
        when(redisTemplate.hasKey("notify:cooldown:100")).thenReturn(false);
        beginTransaction();

        detector.evaluate(100L, 69, now, () -> { });

        verify(valueOperations, never()).set("notify:armed:100", "1", Duration.ofHours(48));
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(valueOperations).set("notify:armed:100", "1", Duration.ofHours(48));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void confirmNextCandidate(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(result);
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction(int status) {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            if (status == TransactionSynchronization.STATUS_COMMITTED) {
                synchronization.afterCommit();
            }
            synchronization.afterCompletion(status);
        }
    }
}
