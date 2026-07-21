package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LiveScoringServiceStateTest {

    @Test
    void handle_shouldCleanupInsteadOfScoringWhenGameAlreadyFinished() {
        long gameId = 8712499L;
        Instant observedAt = Instant.parse("2026-07-15T02:18:13Z");
        GameRepository gameRepository = mock(GameRepository.class);
        PlayRepository playRepository = mock(PlayRepository.class);
        WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
        LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.STATUS_FINAL);
        game.setLifecycleState("FINAL");
        when(watchScoreRepository.existsByGameIdAndComputedAt(gameId, observedAt)).thenReturn(false);
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));

        LiveScoringService service = new LiveScoringService(
                gameRepository,
                playRepository,
                watchScoreRepository,
                mock(ScoreCalculator.class),
                mock(ImportanceCalculator.class),
                mock(GameEventExtractor.class),
                liveSignalPublisher,
                mock(SurgeDetector.class),
                mock(TimelineHighlightTrigger.class),
                mock(SurgeNotificationPublisher.class),
                mock(ScoringProperties.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));

        service.handle(new ScoreTask(gameId, observedAt, 1L, "LIVE", null));

        verify(liveSignalPublisher).removeLiveGame(gameId);
        verify(liveSignalPublisher).evictGameCache(gameId);
        verify(liveSignalPublisher).publishGameSignal(gameId);
        verify(liveSignalPublisher).publishRankingSignal();
        verifyNoInteractions(playRepository);
    }
}
