package com.pulse.replay.rescore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.ScoringProperties;
import com.pulse.scorer.ScoreCalculator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HistoricalScoreReplayServiceTest {

    private final RescoreJdbcRepository repository = mock(RescoreJdbcRepository.class);
    private final ScoringProperties scoringProperties = scoringProperties();
    private final ScoreCalculator calculator = new ScoreCalculator(scoringProperties);

    @Test
    void backfilled그룹은Play마다합성시각으로점수를저장한다() {
        Instant observedAt = Instant.parse("2026-07-11T01:20:00Z");
        when(repository.gameIdsWithPlays()).thenReturn(List.of(5059180L));
        when(repository.playsForGame(5059180L)).thenReturn(List.of(
                playRow(5059180L, 20L, observedAt, true),
                playRow(5059180L, 10L, observedAt, true),
                playRow(5059180L, 30L, observedAt, true)));
        when(repository.insertWatchScore(any())).thenReturn(1);

        service(List.of()).replayAll();

        ArgumentCaptor<RescoreWatchScoreRow> captor = ArgumentCaptor.forClass(RescoreWatchScoreRow.class);
        verify(repository, times(3)).insertWatchScore(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(RescoreWatchScoreRow::playOrder)
                .containsExactly(10L, 20L, 30L);
        assertThat(captor.getAllValues())
                .extracting(RescoreWatchScoreRow::computedAt)
                .containsExactly(observedAt, observedAt.plusSeconds(1), observedAt.plusSeconds(2));
        assertThat(captor.getAllValues())
                .extracting(RescoreWatchScoreRow::scoringVersion)
                .containsOnly(5);
    }

    @Test
    void 일반그룹은관측시각당한건만저장한다() {
        Instant observedAt = Instant.parse("2026-07-11T00:30:00Z");
        when(repository.gameIdsWithPlays()).thenReturn(List.of(5059180L));
        when(repository.playsForGame(5059180L)).thenReturn(List.of(
                playRow(5059180L, 10L, observedAt, false),
                playRow(5059180L, 20L, observedAt, false)));
        when(repository.insertWatchScore(any())).thenReturn(1);

        service(List.of()).replayAll();

        ArgumentCaptor<RescoreWatchScoreRow> captor = ArgumentCaptor.forClass(RescoreWatchScoreRow.class);
        verify(repository).insertWatchScore(captor.capture());
        assertThat(captor.getValue().computedAt()).isEqualTo(observedAt);
        assertThat(captor.getValue().playOrder()).isEqualTo(20L);
    }

    @Test
    void gameIds가지정되면해당경기만재생한다() {
        when(repository.gameIdsWithPlays()).thenReturn(List.of(100L, 200L, 300L));
        when(repository.playsForGame(200L)).thenReturn(List.of());

        service(List.of(200L)).replayAll();

        verify(repository).playsForGame(200L);
        verify(repository, times(1)).playsForGame(any());
    }

    private HistoricalScoreReplayService service(List<Long> gameIds) {
        return new HistoricalScoreReplayService(
                repository,
                calculator,
                scoringProperties,
                new RescoreProperties(gameIds));
    }

    private static RescorePlayRow playRow(long gameId, long playOrder, Instant observedAt, boolean backfilled) {
        return new RescorePlayRow(
                gameId,
                playOrder,
                "Play Result",
                7,
                "Top",
                3,
                2,
                false,
                0,
                1,
                1,
                1,
                observedAt,
                backfilled,
                backfilled ? "S3_BACKFILL" : "S3_LIVE_ARCHIVE");
    }

    private static ScoringProperties scoringProperties() {
        return new ScoringProperties(
                5,
                new ScoringProperties.LateInning(6, 12, 18),
                new ScoringProperties.ScoreGap(15, 9, 3),
                new ScoringProperties.RecentScore(6, 15, 180,
                        Map.of("gap-0", 2.0, "gap-1", 1.5, "gap-2", 1.2, "default", 1.0)),
                new ScoringProperties.LeadChange(9, 12),
                new ScoringProperties.BigInning(9, 2),
                new ScoringProperties.CountPressure(3, 3, 5),
                new ScoringProperties.Pressure(6, 4),
                new ScoringProperties.EarlySlugfest(5, 3, 7),
                new ScoringProperties.Importance(0.9, 1.15, 1.15, 1.10, 1.05, 0.90, 10, 90),
                10,
                new ScoringProperties.Personalization(10, 5, 15),
                new ScoringProperties.Pregame(30, 0.15, 0.25, 20, 4.6, 2.0, 30, 15),
                new ScoringProperties.Detail(100, 100, 2.0, 10, 8, 8),
                new ScoringProperties.TensionCurve(List.of(20, 40, 60, 80)),
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 3, 15, 70, 20));
    }
}
