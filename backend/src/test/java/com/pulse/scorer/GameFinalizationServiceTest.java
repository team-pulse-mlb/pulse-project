package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class GameFinalizationServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final TimelineHighlightBackfill timelineHighlightBackfill =
            mock(TimelineHighlightBackfill.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final GameFinalizationService service = new GameFinalizationService(
            gameRepository,
            liveSignalPublisher,
            aiGenerationTrigger,
            new AfterCommitExecutor(),
            timelineHighlightBackfill,
            redisTemplate
    );

    private final Instant observedAt = Instant.parse("2026-07-08T04:00:00Z");

    @Test
    void hasFinalizationRecord_shouldUseExplicitFinalizationKey() {
        when(redisTemplate.hasKey("score:finalized:100")).thenReturn(true);

        assertThat(service.hasFinalizationRecord(100L)).isTrue();
    }

    @Test
    void handle_shouldRemoveLiveStateOnce() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verify(timelineHighlightBackfill).backfillIfEmpty(100L, observedAt, true);
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldRetryLiveStateCleanupForDuplicateTerminalTask() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(false);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verify(timelineHighlightBackfill).backfillIfEmpty(100L, observedAt, true);
        verifyNoInteractions(aiGenerationTrigger);
    }

    @Test
    void handle_shouldNotRequestAiForSuspendedGame() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.SUSPENDED_POSTPONED.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verifyNoInteractions(timelineHighlightBackfill);
        verify(aiGenerationTrigger, never()).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldNotRequestAiForCanceledDoneGame() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game(Game.STATUS_CANCELED)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.DONE.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verifyNoInteractions(timelineHighlightBackfill);
        verify(aiGenerationTrigger, never()).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldNotRequestAiWhenFinalLifecycleGameWasPostponed() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game(Game.STATUS_POSTPONED)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(liveSignalPublisher).removeLiveGame(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verifyNoInteractions(timelineHighlightBackfill);
        verify(aiGenerationTrigger, never()).onGameFinalized(100L, observedAt);
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
}
