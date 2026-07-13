package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
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

    private static BacktestProperties options() {
        return new BacktestProperties("6", null, null, null, List.of(), List.of(), null,
                15, 25, 12, 10, 0.7, 0.02, 5, 2);
    }
    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return new BacktestModels.PlayRow(1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, null, true, "S3_BACKFILL", null, null, null);
    }
}
