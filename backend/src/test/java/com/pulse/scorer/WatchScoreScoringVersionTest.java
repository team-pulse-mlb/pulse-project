package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.ranking.RankingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class WatchScoreScoringVersionTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant OBSERVED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    @DisplayName("라이브 점수에 적용한 scoring version을 저장한다")
    void liveScoreStoresScoringVersion() {
        ScoringProperties properties = TestScoringProperties.version5();
        GameRepository gameRepository = mock(GameRepository.class);
        PlayRepository playRepository = mock(PlayRepository.class);
        WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
        ImportanceCalculator importanceCalculator = mock(ImportanceCalculator.class);

        when(watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT)).thenReturn(false);
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(liveGame()));
        when(importanceCalculator.multiplier(any(Game.class))).thenReturn(1.0);

        LiveScoringService service = new LiveScoringService(
                gameRepository,
                playRepository,
                watchScoreRepository,
                new ScoreCalculator(properties),
                importanceCalculator,
                mock(GameEventExtractor.class),
                mock(LiveSignalPublisher.class),
                mock(SurgeDetector.class),
                mock(TimelineHighlightTrigger.class),
                mock(AiGenerationTrigger.class),
                mock(NotificationEventPublisher.class),
                properties);

        service.handle(new ScoreTask(GAME_ID, OBSERVED_AT, null, "LIVE", null));

        assertStoredVersion(watchScoreRepository, properties.version());
    }

    @Test
    @DisplayName("운영 재계산 점수에 적용한 scoring version을 저장한다")
    void recalculatedScoreStoresScoringVersion() {
        ScoringProperties properties = TestScoringProperties.version5();
        GameRepository gameRepository = mock(GameRepository.class);
        PlayRepository playRepository = mock(PlayRepository.class);
        WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);

        when(watchScoreRepository.existsByGameIdAndComputedAt(GAME_ID, OBSERVED_AT)).thenReturn(false);
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(liveGame()));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(eq(GAME_ID), any(Pageable.class)))
                .thenReturn(List.of());

        ScoreRecalculationService service = new ScoreRecalculationService(
                gameRepository,
                playRepository,
                watchScoreRepository,
                new ScoreCalculator(properties),
                mock(RankingService.class),
                properties);

        service.recalculate(GAME_ID, OBSERVED_AT);

        assertStoredVersion(watchScoreRepository, properties.version());
    }

    private static void assertStoredVersion(WatchScoreRepository repository, int expectedVersion) {
        ArgumentCaptor<WatchScore> captor = ArgumentCaptor.forClass(WatchScore.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getScoringVersion()).isEqualTo(expectedVersion);
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
