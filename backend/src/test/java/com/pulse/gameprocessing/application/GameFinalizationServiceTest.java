package com.pulse.gameprocessing.application;

import com.pulse.gameprocessing.aicopy.AiGenerationTrigger;
import com.pulse.gameprocessing.effect.LiveSignalPublisher;
import com.pulse.gameprocessing.highlight.TimelineHighlightBackfill;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GameFinalizationServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final TimelineHighlightBackfill timelineHighlightBackfill = mock(TimelineHighlightBackfill.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final GameFinalizationService service = new GameFinalizationService(
            gameRepository,
            liveSignalPublisher,
            aiGenerationTrigger,
            new AfterCommitExecutor(),
            timelineHighlightBackfill,
            redisTemplate
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
    @DisplayName("복구 러너의 완료 판정은 lifecycle별 DB 기록을 따른다")
    void hasFinalizationRecord_shouldUseDatabaseRecordPerLifecycle() {
        Game finalized = game();
        finalized.setLifecycleState(GameLifecycle.FINAL.name());
        finalized.setFinalizedAt(observedAt);
        when(gameRepository.findById(100L)).thenReturn(Optional.of(finalized));
        assertThat(service.hasFinalizationRecord(100L)).isTrue();

        Game pendingFinal = game();
        pendingFinal.setLifecycleState(GameLifecycle.FINAL.name());
        // POSTPONED 기록만 있어도 FINAL 재발행 대상에서 빠지지 않아야 한다.
        pendingFinal.setTerminalSuspendedPostponedAt(observedAt);
        when(gameRepository.findById(200L)).thenReturn(Optional.of(pendingFinal));
        assertThat(service.hasFinalizationRecord(200L)).isFalse();
    }

    @Test
    @DisplayName("DB에서 최초 종료 처리를 확정하고 AI 생성을 요청한다")
    void handle_shouldMarkFinalizedInDatabaseAndRequestAiForFinal() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(gameRepository.markFinalized(100L, observedAt)).thenReturn(1);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(redisTemplate).delete(List.of("notify:armed:100", "notify:cooldown:100"));
        verify(gameRepository).markFinalized(100L, observedAt);
        verify(timelineHighlightBackfill).backfillIfEmpty(100L, observedAt, true);
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldNotRunAfterCommitWorkWhenTransactionRollsBack() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(gameRepository.markFinalized(100L, observedAt)).thenReturn(1);
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
        when(gameRepository.markSuspendedPostponed(100L, observedAt)).thenReturn(1);
        when(gameRepository.markFinalized(100L, observedAt)).thenReturn(1);

        service.handle(task(GameLifecycle.SUSPENDED_POSTPONED.name()));
        service.handle(task(GameLifecycle.FINAL.name()));

        verify(gameRepository).markSuspendedPostponed(100L, observedAt);
        verify(gameRepository).markFinalized(100L, observedAt);
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldUseSeparateDoneRecordAndSkipAiForCanceledGame() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game(Game.STATUS_CANCELED)));
        when(gameRepository.markDone(100L, observedAt)).thenReturn(1);

        service.handle(task(GameLifecycle.DONE.name()));

        verify(gameRepository).markDone(100L, observedAt);
        verify(gameRepository, never()).markFinalized(100L, observedAt);
        verifyNoInteractions(timelineHighlightBackfill);
        verifyNoInteractions(aiGenerationTrigger);
    }

    @Test
    @DisplayName("Redis가 초기화되어도 DB 기록으로 중복 종료 후처리를 막는다")
    void handle_shouldKeepFinalizationIdempotentAfterRedisReset() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(gameRepository.markFinalized(100L, observedAt)).thenReturn(1, 0);

        service.handle(task(GameLifecycle.FINAL.name()));
        service.handle(task(GameLifecycle.FINAL.name()));

        verify(liveSignalPublisher, times(2)).removeLiveGame(100L);
        verify(liveSignalPublisher, times(2)).evictGameCache(100L);
        verify(liveSignalPublisher, times(2)).publishGameSignal(100L);
        verify(liveSignalPublisher, times(2)).publishRankingSignal();
        verify(gameRepository, times(2)).markFinalized(100L, observedAt);
        verify(timelineHighlightBackfill).backfillIfEmpty(100L, observedAt, true);
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
        verify(redisTemplate, never()).opsForValue();
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
