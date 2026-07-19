package com.pulse.replay.rescore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import com.pulse.replay.backtest.BacktestModels;
import com.pulse.replay.backtest.BacktestProperties;
import com.pulse.replay.backtest.GameReplayEngine;
import com.pulse.scorer.ScoreCalculator;
import com.pulse.scorer.ScoringInput;
import java.math.BigDecimal;
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

    @Test
    void 동일한_플레이에서_rescore도_live의_최종_점수_공식을_적용한다() {
        Instant observedAt = Instant.parse("2026-07-11T01:20:00Z");
        when(repository.gameIdsWithPlays()).thenReturn(List.of(5059180L));
        when(repository.playsForGame(5059180L)).thenReturn(List.of(
                playRow(5059180L, 10L, observedAt, false)));
        when(repository.insertWatchScore(any())).thenReturn(1);

        service(List.of()).replayAll();

        ArgumentCaptor<RescoreWatchScoreRow> captor = ArgumentCaptor.forClass(RescoreWatchScoreRow.class);
        verify(repository).insertWatchScore(captor.capture());
        RescoreWatchScoreRow score = captor.getValue();
        double liveWatchScore = calculator.clampWatchScore(
                score.baseScore() * scoringProperties.importance().bothContending()
                        + scoringProperties.pregameCarryoverMax());

        assertThat(score.watchScore()).isEqualTo((int) Math.round(liveWatchScore));
    }

    @Test
    void 고정_플레이_시퀀스의_live_backtest_rescore_점수가_일치한다() {
        Instant firstObservedAt = Instant.parse("2026-07-11T01:20:00Z");
        List<RescorePlayRow> rows = goldenRows(firstObservedAt);
        when(repository.gameIdsWithPlays()).thenReturn(List.of(5059180L));
        when(repository.playsForGame(5059180L)).thenReturn(rows);
        when(repository.insertWatchScore(any())).thenReturn(1);

        service(List.of()).replayAll();

        ArgumentCaptor<RescoreWatchScoreRow> captor = ArgumentCaptor.forClass(RescoreWatchScoreRow.class);
        verify(repository, times(3)).insertWatchScore(captor.capture());
        RescoreWatchScoreRow rescore = captor.getAllValues().get(2);

        Game liveGame = game();
        liveGame.setPeriod(8);
        liveGame.setHomeRuns(4);
        liveGame.setAwayRuns(4);
        List<Play> livePlays = rows.stream().map(HistoricalScoreReplayServiceTest::play).toList();
        ScoreCalculator.Result live = calculator.calculate(new ScoringInput(
                liveGame,
                livePlays,
                ScoreTask.Situation.of(2, 3, 2, true, true, true),
                0,
                firstObservedAt.plusSeconds(60),
                scoringProperties.importance().bothContending(),
                100));

        GameReplayEngine engine = new GameReplayEngine(backtestProperties(), new ObjectMapper());
        List<BacktestModels.Cycle> cycles = engine.replay(backtestData(rows), scoringProperties);
        BacktestModels.Cycle backtest = cycles.get(cycles.size() - 1);

        int liveScore = (int) Math.round(live.watchScore());
        int liveBaseScore = (int) Math.round(live.baseScore());
        assertThat((int) Math.round(backtest.baseScore())).isEqualTo(liveBaseScore);
        assertThat(rescore.baseScore()).isEqualTo(liveBaseScore);
        assertThat((int) Math.round(backtest.watchScore())).isEqualTo(liveScore);
        assertThat(rescore.watchScore()).isEqualTo(liveScore);
        assertThat(rescore.importanceMultiplier()).isEqualByComparingTo("1.10");
        assertThat(rescore.pregameBonus()).isEqualByComparingTo("10.00");
        assertThat(rescore.signalContributions().get("pressure"))
                .isEqualTo(scoringProperties.pressure().basesLoaded());
    }

    private HistoricalScoreReplayService service(List<Long> gameIds) {
        when(repository.gameForScoring(any())).thenReturn(game());
        when(repository.playoffPercentAt(eq(10L), any())).thenReturn(BigDecimal.valueOf(50));
        when(repository.playoffPercentAt(eq(20L), any())).thenReturn(BigDecimal.valueOf(60));
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
                true,
                true,
                true,
                observedAt,
                backfilled,
                backfilled ? "S3_BACKFILL" : "S3_LIVE_ARCHIVE");
    }

    private static List<RescorePlayRow> goldenRows(Instant firstObservedAt) {
        return List.of(
                goldenRow(10, 2, 3, false, null, 0, 1, 1, false, false, false, firstObservedAt),
                goldenRow(20, 4, 3, true, 2, 1, 2, 1, false, true, false, firstObservedAt.plusSeconds(30)),
                goldenRow(30, 4, 4, true, 1, 2, 3, 2, true, true, true, firstObservedAt.plusSeconds(60)));
    }

    private static RescorePlayRow goldenRow(
            long order,
            int homeScore,
            int awayScore,
            boolean scoringPlay,
            Integer scoreValue,
            int outs,
            int balls,
            int strikes,
            boolean runnerOnFirst,
            boolean runnerOnSecond,
            boolean runnerOnThird,
            Instant observedAt
    ) {
        return new RescorePlayRow(
                5059180L, order, "Play Result", 8, order == 30 ? "BOTTOM" : "TOP",
                homeScore, awayScore, scoringPlay, scoreValue, outs, balls, strikes,
                runnerOnFirst, runnerOnSecond, runnerOnThird,
                observedAt, false, "S3_LIVE_ARCHIVE");
    }

    private static Play play(RescorePlayRow row) {
        Play play = new Play();
        play.setGameId(row.gameId());
        play.setPlayOrder(row.playOrder());
        play.setType(row.type());
        play.setInning(row.inning());
        play.setInningType(row.inningType());
        play.setHomeScore(row.homeScore());
        play.setAwayScore(row.awayScore());
        play.setScoringPlay(row.scoringPlay());
        play.setScoreValue(row.scoreValue());
        play.setOuts(row.outs());
        play.setBalls(row.balls());
        play.setStrikes(row.strikes());
        play.setRunnerOnFirst(row.runnerOnFirst());
        play.setRunnerOnSecond(row.runnerOnSecond());
        play.setRunnerOnThird(row.runnerOnThird());
        play.setFetchedAt(row.observedAt());
        return play;
    }

    private static BacktestModels.GameData backtestData(List<RescorePlayRow> rows) {
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                5059180L,
                Instant.parse("2026-07-11T00:00:00Z"),
                Game.STATUS_IN_PROGRESS,
                false,
                10L,
                20L,
                4,
                4,
                8,
                100,
                "{\"components\":{\"starterMatchup\":{\"score\":100}}}",
                "HOME",
                "AWAY");
        List<BacktestModels.PlayRow> plays = rows.stream()
                .map(row -> new BacktestModels.PlayRow(
                        row.gameId(), row.playOrder(), row.type(), row.inning(), row.inningType(),
                        row.homeScore(), row.awayScore(), row.scoringPlay(), row.scoreValue(),
                        row.outs(), row.balls(), row.strikes(), row.observedAt(), row.backfilled(), row.source(),
                        row.runnerOnFirst(), row.runnerOnSecond(), row.runnerOnThird()))
                .toList();
        BacktestModels.StandingRow home = new BacktestModels.StandingRow(
                BigDecimal.valueOf(50), BigDecimal.valueOf(0.55));
        BacktestModels.StandingRow away = new BacktestModels.StandingRow(
                BigDecimal.valueOf(60), BigDecimal.valueOf(0.53));
        return new BacktestModels.GameData(game, plays, home, away, List.of());
    }

    private static BacktestProperties backtestProperties() {
        return new BacktestProperties(
                "5", null, null, null, List.of(), List.of(), null, null,
                15, 25, 12, 2, 10, 0.7, 0.02, 5, 2);
    }

    private static Game game() {
        Game game = new Game();
        game.setId(5059180L);
        game.setStartTime(Instant.parse("2026-07-11T00:00:00Z"));
        game.setStatus(Game.STATUS_IN_PROGRESS);
        game.setHomeTeamId(10L);
        game.setAwayTeamId(20L);
        game.setPregameScore(100);
        return game;
    }

    private static ScoringProperties scoringProperties() {
        return new ScoringProperties(
                5,
                new ScoringProperties.LateInning(6, 12, 18),
                new ScoringProperties.ScoreGap(15, 9, 3),
                new ScoringProperties.RecentScore(6, 15, 180,
                        Map.of("gap-0", 2.0, "gap-1", 1.5, "gap-2", 1.2, "default", 1.0)),
                new ScoringProperties.LeadChange(9, 12, 300),
                new ScoringProperties.BigInning(9, 2),
                new ScoringProperties.CountPressure(3, 3, 5),
                new ScoringProperties.Pressure(6, 4, 0, null),
                new ScoringProperties.EarlySlugfest(5, 3, 7),
                new ScoringProperties.Importance(0.9, 1.15, 1.15, 1.10, 1.05, 0.90, 10, 90),
                10,
                new ScoringProperties.Personalization(10, 5, 15),
                new ScoringProperties.Pregame(30, 0.15, 0.25, 20, 4.6, 2.0, 30, 15),
                new ScoringProperties.Detail(100, 100, 2.0, 10, 8, 8),
                new ScoringProperties.TensionCurve(List.of(20, 40, 60, 80)),
                new ScoringProperties.Thresholds(85, 70, 15, 5, 15, 3, 15, 70, 20),
                new ScoringProperties.Highlight(false, 40, 12, 6, 8, 8));
    }
}
