package com.pulse.scorer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GameFinalizationServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final TimelineHighlightBackfill timelineHighlightBackfill = mock(TimelineHighlightBackfill.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final GameFinalizationService service = new GameFinalizationService(
            gameRepository,
            liveSignalPublisher,
            aiGenerationTrigger,
            new AfterCommitExecutor(),
            timelineHighlightBackfill,
            redisTemplate,
            Duration.ofHours(48)
    );

    private final Instant observedAt = Instant.parse("2026-07-08T04:00:00Z");

    @AfterEach
    void cleanUpTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void handle_shouldCreateFinalizedKeyWithTtlAndRequestAiForFinal() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "score:finalized:100", observedAt.toString(), Duration.ofHours(48)))
                .thenReturn(true);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(redisTemplate).delete(List.of("notify:armed:100", "notify:cooldown:100"));
        verify(timelineHighlightBackfill).backfillIfEmpty(100L, observedAt, true);
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldNotCreateRedisIdempotencyStateWhenTransactionRollsBack() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        beginTransaction();

        service.handle(task(GameLifecycle.FINAL.name()));
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(aiGenerationTrigger);
    }

    @Test
    void handle_shouldLetFinalProceedAfterPostponedTask() {
        when(gameRepository.findById(100L)).thenReturn(
                Optional.of(game(Game.STATUS_POSTPONED)),
                Optional.of(game(Game.STATUS_FINAL)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "score:terminal:100:SUSPENDED_POSTPONED", observedAt.toString(), Duration.ofHours(48)))
                .thenReturn(true);
        when(valueOperations.setIfAbsent(
                "score:finalized:100", observedAt.toString(), Duration.ofHours(48)))
                .thenReturn(true);

        service.handle(task(GameLifecycle.SUSPENDED_POSTPONED.name()));
        service.handle(task(GameLifecycle.FINAL.name()));

        verify(valueOperations).setIfAbsent(
                "score:terminal:100:SUSPENDED_POSTPONED", observedAt.toString(), Duration.ofHours(48));
        verify(valueOperations).setIfAbsent(
                "score:finalized:100", observedAt.toString(), Duration.ofHours(48));
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldUseSeparateKeyAndSkipAiForCanceledDoneGame() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game(Game.STATUS_CANCELED)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "score:terminal:100:DONE", observedAt.toString(), Duration.ofHours(48)))
                .thenReturn(true);

        service.handle(task(GameLifecycle.DONE.name()));

        verify(valueOperations).setIfAbsent(
                "score:terminal:100:DONE", observedAt.toString(), Duration.ofHours(48));
        verify(valueOperations, never()).setIfAbsent(
                "score:finalized:100", observedAt.toString(), Duration.ofHours(48));
        verifyNoInteractions(timelineHighlightBackfill);
        verifyNoInteractions(aiGenerationTrigger);
    }

    @Test
    void handle_shouldRetryLiveStateCleanupForDuplicateFinalTask() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(
                "score:finalized:100", observedAt.toString(), Duration.ofHours(48)))
                .thenReturn(false);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verify(redisTemplate).expire("score:finalized:100", Duration.ofHours(48));
        verifyNoInteractions(aiGenerationTrigger);
    }

    private ScoreTask task(String lifecycleState) {
        return new ScoreTask(100L, observedAt, 12L, lifecycleState, null);
    }

    private static Game game() {
        return game(Game.STATUS_FINAL);
    }

    private static Game game(String status) {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(status);
        return game;
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
