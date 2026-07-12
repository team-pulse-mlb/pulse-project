package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MetricsCalculatorTest {
    @Test void 순위_상관을_동점과_함께_계산한다() {
        assertThat(MetricsCalculator.spearman(List.of(1.0, 2.0, 2.0, 4.0), List.of(1.0, 2.0, 2.0, 4.0))).isEqualTo(1.0);
        assertThat(MetricsCalculator.kendall(List.of(1.0, 2.0, 2.0, 4.0), List.of(1.0, 2.0, 2.0, 4.0))).isEqualTo(1.0);
    }
    @Test void auc는_동점에_절반을_부여한다() {
        assertThat(MetricsCalculator.auc(List.of(0, 1, 0, 1), List.of(0.1, 0.9, 0.5, 0.5))).isEqualTo(0.875);
    }
    @Test void auc는_라벨이_한_종류면_산출하지_않는다() {
        assertThat(MetricsCalculator.auc(List.of(1, 1), List.of(0.1, 0.2))).isNull();
    }

    @Test void horizon_안의_득점과_리드_변경을_양성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 0, 0, false), play(2, 0, 0, false), play(3, 0, 1, true), play(4, 2, 1, false));
        List<BacktestModels.Cycle> cycles = List.of(
                new BacktestModels.Cycle(null, 1, 1, 1, 0, "S3_BACKFILL"),
                new BacktestModels.Cycle(null, 3, 1, 1, 2, "S3_BACKFILL"));
        assertThat(MetricsCalculator.aucLabels(plays, cycles, 2)).containsExactly(1, 1);
    }

    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return new BacktestModels.PlayRow(1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, null, true, "S3_BACKFILL", null, null, null);
    }
}
