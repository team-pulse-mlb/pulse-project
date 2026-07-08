package com.pulse.scorer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.poller.GameLifecycle;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class GameFinalizationServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final ReplaySegmentService replaySegmentService = mock(ReplaySegmentService.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final GameFinalizationService service = new GameFinalizationService(
            gameRepository,
            replaySegmentService,
            rankingService,
            liveSignalPublisher,
            aiGenerationTrigger,
            redisTemplate
    );

    private final Instant observedAt = Instant.parse("2026-07-08T04:00:00Z");

    @Test
    void handle_shouldCloseReplayAndRemoveLiveStateOnce() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.FINAL.name()));

        verify(replaySegmentService).closeOpenSegment(100L, observedAt);
        verify(rankingService).removeLive(100L);
        verify(liveSignalPublisher).evictGameCache(100L);
        verify(liveSignalPublisher).publishGameSignal(100L);
        verify(liveSignalPublisher).publishRankingSignal();
        verify(aiGenerationTrigger).onGameFinalized(100L, observedAt);
    }

    @Test
    void handle_shouldSkipDuplicateTerminalTask() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(false);

        service.handle(task(GameLifecycle.FINAL.name()));

        verifyNoInteractions(replaySegmentService, rankingService, liveSignalPublisher, aiGenerationTrigger);
    }

    @Test
    void handle_shouldKeepOpenReplaySegmentForSuspendedGame() {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent("score:finalized:100", observedAt.toString())).thenReturn(true);

        service.handle(task(GameLifecycle.SUSPENDED_POSTPONED.name()));

        verify(replaySegmentService, never()).closeOpenSegment(100L, observedAt);
        verify(rankingService).removeLive(100L);
        verify(aiGenerationTrigger, never()).onGameFinalized(100L, observedAt);
    }

    private ScoreTask task(String lifecycleState) {
        return new ScoreTask(100L, observedAt, 12L, lifecycleState, null);
    }

    private static Game game() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_FINAL);
        return game;
    }
}
