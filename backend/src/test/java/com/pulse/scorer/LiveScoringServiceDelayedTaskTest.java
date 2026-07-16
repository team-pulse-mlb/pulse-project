package com.pulse.scorer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class LiveScoringServiceDelayedTaskTest {

    private static final long GAME_ID = 5059180L;
    private static final long LAST_PLAY_ORDER = 987654L;
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void handle_shouldReadPlaysOnlyThroughLastObservedPlayOrder() {
        TestFixture fixture = new TestFixture();
        Pageable pageable = PageRequest.of(0, fixture.properties.leadChange().windowPlays() + 1);
        when(fixture.playRepository.findByGameIdAndPlayOrderLessThanEqualOrderByPlayOrderDesc(
                GAME_ID, LAST_PLAY_ORDER, pageable)).thenReturn(List.of());

        fixture.service.handle(new ScoreTask(GAME_ID, OBSERVED_AT, LAST_PLAY_ORDER, "LIVE", null));

        verify(fixture.playRepository).findByGameIdAndPlayOrderLessThanEqualOrderByPlayOrderDesc(
                GAME_ID, LAST_PLAY_ORDER, pageable);
        verify(fixture.playRepository, never())
                .findByGameIdOrderByPlayOrderDesc(eq(GAME_ID), any(Pageable.class));
    }

    @Test
    void handle_shouldCalculateWithNoPlaysWhenLastObservedPlayOrderIsNull() {
        TestFixture fixture = new TestFixture();

        fixture.service.handle(new ScoreTask(GAME_ID, OBSERVED_AT, null, "LIVE", null));

        verifyNoInteractions(fixture.playRepository);
        verify(fixture.calculator).calculate(fixture.game, List.of(), null, 0, OBSERVED_AT);
    }

    private static final class TestFixture {

        private final ScoringProperties properties = TestScoringProperties.version5();
        private final GameRepository gameRepository = mock(GameRepository.class);
        private final PlayRepository playRepository = mock(PlayRepository.class);
        private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
        private final ScoreCalculator calculator = mock(ScoreCalculator.class);
        private final ImportanceCalculator importanceCalculator = mock(ImportanceCalculator.class);
        private final Game game = liveGame();
        private final LiveScoringService service;

        private TestFixture() {
            when(watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT)).thenReturn(false);
            when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(GAME_ID)).thenReturn(Optional.empty());
            when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
            when(calculator.calculate(eq(game), any(), eq(null), eq(0), eq(OBSERVED_AT)))
                    .thenReturn(new ScoreCalculator.Result(Map.of(), 0, false));
            when(calculator.clampWatchScore(anyDouble())).thenReturn(0.0);
            when(importanceCalculator.multiplier(game)).thenReturn(1.0);

            service = new LiveScoringService(
                    gameRepository,
                    playRepository,
                    watchScoreRepository,
                    calculator,
                    importanceCalculator,
                    mock(GameEventExtractor.class),
                    mock(LiveSignalPublisher.class),
                    mock(SurgeDetector.class),
                    mock(TimelineHighlightTrigger.class),
                    mock(AiGenerationTrigger.class),
                    mock(NotificationEventPublisher.class),
                    properties);
        }
    }

    private static Game liveGame() {
        Game game = new Game();
        game.setId(GAME_ID);
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setLifecycleState("LIVE");
        game.setPeriod(7);
        game.setHomeRuns(2);
        game.setAwayRuns(2);
        return game;
    }
}
