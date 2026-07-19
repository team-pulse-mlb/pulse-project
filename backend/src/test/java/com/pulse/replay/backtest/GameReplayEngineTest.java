package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameReplayEngineTest {
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

    private static BacktestProperties options() {
        return new BacktestProperties("6", null, null, null, List.of(), List.of(), null, null,
                15, 25, 12, 2, 10, 0.7, 0.02, 5, 2);
    }
    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return new BacktestModels.PlayRow(1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, null, true, "S3_BACKFILL", null, null, null);
    }
}
