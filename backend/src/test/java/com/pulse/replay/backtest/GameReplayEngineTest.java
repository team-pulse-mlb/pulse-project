package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameReplayEngineTest {
    @Test void 깨진_경기전_입력_JSON은_예외_없이_영점으로_처리한다() {
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());

        assertThat(engine.starterScore(123L, "{broken-json")).isZero();
    }

    @Test void 백필_득점은_15_play_동안_선형_감쇠한다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        List<BacktestModels.PlayRow> rows = new ArrayList<>();
        rows.add(play(1, 1, 0, true));
        for (int index = 2; index <= 16; index++) rows.add(play(index, 1, 0, false));
        assertThat(engine.approximateRecent(rows, 0, scoring)).isPositive();
        assertThat(engine.approximateRecent(rows, 15, scoring)).isZero();
    }

    @Test void 백필_리드_변경은_25_play_윈도에서만_유지한다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        List<BacktestModels.PlayRow> rows = new ArrayList<>();
        rows.add(play(1, 1, 0, false)); rows.add(play(2, 1, 2, false));
        for (int index = 3; index <= 27; index++) rows.add(play(index, 1, 2, false));
        assertThat(engine.approximateLead(rows, 1, scoring)).isEqualTo(scoring.leadChange().bonus());
        assertThat(engine.approximateLead(rows, 26, scoring)).isZero();
    }

    @Test void 상태_점수는_득점_직후의_사후_반응_신호를_제외한다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                1, Instant.parse("2026-06-01T00:00:00Z"), "LIVE", false,
                1L, 2L, 1, 0, 1, null, null, "홈", "원정");
        BacktestModels.GameData data = new BacktestModels.GameData(
                game, List.of(play(1, 1, 0, true)), null, null, List.of());

        BacktestModels.Cycle cycle = engine.replay(data, scoring).getFirst();

        assertThat(cycle.stateScore()).isEqualTo(scoring.scoreGap().gap01());
        assertThat(cycle.stateScore()).isLessThan(cycle.baseScore());
        assertThat(cycle.signals())
                .containsEntry("recent_score", engine.approximateRecent(data.plays(), 0, scoring))
                .containsEntry("lead_change", engine.approximateLead(data.plays(), 0, scoring));
    }

    @Test void 관측_시각이_있는_play만_있으면_기존처럼_동일_시각을_한_사이클로_묶는다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        Instant firstObservedAt = Instant.parse("2026-06-01T01:00:00Z");
        Instant secondObservedAt = Instant.parse("2026-06-01T01:00:05Z");
        BacktestModels.GameData data = gameData(List.of(
                play(1, 0, 0, false, firstObservedAt, "OPERATIONAL"),
                play(2, 1, 0, true, firstObservedAt, "OPERATIONAL"),
                play(3, 1, 0, false, secondObservedAt, "OPERATIONAL")));

        List<BacktestModels.Cycle> cycles = engine.replay(data, scoring);

        assertThat(cycles).extracting(BacktestModels.Cycle::playOrder).containsExactly(2L, 3L);
        assertThat(cycles).extracting(BacktestModels.Cycle::computedAt)
                .containsExactly(firstObservedAt, secondObservedAt);
        assertThat(cycles).extracting(BacktestModels.Cycle::latestPlayIndex).containsExactly(1, 2);
    }

    @Test void 혼합_source의_관측_시각이_없는_백필_play를_순서대로_독립_사이클에_포함한다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        Instant observedAt = Instant.parse("2026-06-01T01:00:00Z");
        BacktestModels.GameData data = gameData(List.of(
                play(3, 1, 0, false, null, "S3_BACKFILL"),
                play(1, 0, 0, false, null, "S3_BACKFILL"),
                play(2, 1, 0, true, observedAt, "OPERATIONAL")));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Metrics.addRegistry(meterRegistry);

        try {
            List<BacktestModels.Cycle> cycles = engine.replay(data, scoring);

            assertThat(cycles).extracting(BacktestModels.Cycle::playOrder).containsExactly(1L, 2L, 3L);
            assertThat(cycles).extracting(BacktestModels.Cycle::computedAt)
                    .containsExactly(Instant.EPOCH, observedAt, observedAt);
            assertThat(cycles).extracting(BacktestModels.Cycle::latestPlayIndex).containsExactly(0, 1, 2);
            assertThat(meterRegistry.get("pulse.replay.null_observed_included")
                    .tag("source", "S3_BACKFILL")
                    .counter()
                    .count()).isEqualTo(2.0);
        } finally {
            Metrics.removeRegistry(meterRegistry);
            meterRegistry.close();
        }
    }

    @Test void 모든_play가_S3_BACKFILL이면_관측_시각과_무관하게_백필_경로를_유지한다() {
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        GameReplayEngine engine = new GameReplayEngine(options(), new ObjectMapper());
        Instant observedAt = Instant.parse("2026-06-01T01:00:00Z");
        BacktestModels.GameData data = gameData(List.of(
                play(1, 0, 0, false, observedAt, "S3_BACKFILL"),
                play(2, 1, 0, true, observedAt, "S3_BACKFILL")));

        List<BacktestModels.Cycle> cycles = engine.replay(data, scoring);

        assertThat(cycles).extracting(BacktestModels.Cycle::playOrder).containsExactly(1L, 2L);
        assertThat(cycles).extracting(BacktestModels.Cycle::computedAt).containsOnlyNulls();
    }

    private static BacktestProperties options() {
        return new BacktestProperties("6", null, null, null, List.of(), List.of(), null, null,
                15, 25, 12, 2, 10, 0.7, 0.02, 5, 2);
    }
    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return play(order, home, away, scoring, null, "S3_BACKFILL");
    }
    private static BacktestModels.PlayRow play(
            long order,
            int home,
            int away,
            boolean scoring,
            Instant observedAt,
            String source
    ) {
        return new BacktestModels.PlayRow(1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, observedAt, true, source, null, null, null);
    }
    private static BacktestModels.GameData gameData(List<BacktestModels.PlayRow> plays) {
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                1, Instant.parse("2026-06-01T00:00:00Z"), "LIVE", false,
                1L, 2L, 1, 0, 1, null, null, "홈", "원정");
        return new BacktestModels.GameData(game, plays, null, null, List.of());
    }
}
