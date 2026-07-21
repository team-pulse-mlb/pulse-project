package com.pulse.scorer;

import com.pulse.scoring.ImportanceCalculator;
import com.pulse.scoring.ScoreCalculator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScoreRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

class LiveScoringPlayTranslationTriggerTest {

    private static final long GAME_ID = 5059180L;

    private static final long LAST_PLAY_ORDER = 987654L;

    private static final Instant OBSERVED_AT =
            Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void handle_shouldPublishEventCarryingLastObservedPlayOrder() {
        ScoringProperties properties =
                TestScoringProperties.version5();

        GameRepository gameRepository =
                mock(GameRepository.class);

        PlayRepository playRepository =
                mock(PlayRepository.class);

        WatchScoreRepository watchScoreRepository =
                mock(WatchScoreRepository.class);

        ImportanceCalculator importanceCalculator =
                mock(ImportanceCalculator.class);

        ApplicationEventPublisher applicationEventPublisher =
                mock(ApplicationEventPublisher.class);

        when(
                watchScoreRepository
                        .existsByGameIdAndComputedAt(
                                GAME_ID,
                                OBSERVED_AT))
                .thenReturn(false);

        when(
                watchScoreRepository
                        .findTopByGameIdOrderByComputedAtDesc(
                                GAME_ID))
                .thenReturn(Optional.empty());

        when(gameRepository.findById(GAME_ID))
                .thenReturn(
                        Optional.of(liveGame()));

        when(
                playRepository
                        .findByGameIdAndPlayOrderLessThanEqualOrderByPlayOrderDesc(
                                eq(GAME_ID),
                                eq(LAST_PLAY_ORDER),
                                any(Pageable.class)))
                .thenReturn(List.of());

        when(importanceCalculator.multiplier(any(Game.class)))
                .thenReturn(1.0);

        LiveScoringService service =
                new LiveScoringService(
                        gameRepository,
                        playRepository,
                        watchScoreRepository,
                        new ScoreCalculator(properties),
                        importanceCalculator,
                        mock(GameEventExtractor.class),
                        mock(LiveSignalPublisher.class),
                        mock(SurgeDetector.class),
                        mock(TimelineHighlightTrigger.class),
                        mock(SurgeNotificationPublisher.class),
                        properties,
                        applicationEventPublisher);

        service.handle(
                new ScoreTask(
                        GAME_ID,
                        OBSERVED_AT,
                        LAST_PLAY_ORDER,
                        "LIVE",
                        null));

        /*
         * poller가 한 번에 여러 play를 저장하더라도 scorer는 마지막 관측 순서를
         * 이벤트에 실어야 하고, 커밋 후 PlayTranslationCommitListener가 그 순서까지
         * 미번역 결과 생성을 요청한다.
         */
        ArgumentCaptor<LiveScoreComputedEvent> eventCaptor =
                ArgumentCaptor.forClass(LiveScoreComputedEvent.class);
        verify(applicationEventPublisher)
                .publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().translationThroughPlayOrder())
                .isEqualTo(LAST_PLAY_ORDER);
    }

    private static Game liveGame() {
        Game game =
                new Game();

        game.setId(GAME_ID);
        game.setStatus(
                Game.STATUS_IN_PROGRESS);
        game.setLifecycleState("LIVE");
        game.setPeriod(7);
        game.setHomeRuns(2);
        game.setAwayRuns(2);

        return game;
    }
}
