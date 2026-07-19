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

    @Test void 동점_경유_리드_변경을_양성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 1, 0, false), play(2, 1, 1, false), play(3, 1, 2, false));

        assertThat(MetricsCalculator.aucLabels(plays, List.of(cycle(1)), 2, 2)).containsExactly(1);
    }

    @Test void 동점이_되는_득점을_양성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 1, 0, false), play(2, 1, 1, true));

        assertThat(MetricsCalculator.aucLabels(plays, List.of(cycle(1)), 1, 2)).containsExactly(1);
    }

    @Test void 점수_차_상한_이내의_득점을_양성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 0, 0, false), play(2, 3, 1, true));

        assertThat(MetricsCalculator.aucLabels(plays, List.of(cycle(1)), 1, 2)).containsExactly(1);
    }

    @Test void 점수_차_상한을_넘는_득점을_음성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 5, 0, false), play(2, 6, 0, true));

        assertThat(MetricsCalculator.aucLabels(plays, List.of(cycle(1)), 1, 2)).containsExactly(0);
    }

    @Test void playOrder가_없는_cycle은_음성으로_판정한다() {
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 0, 0, false), play(2, 1, 0, true));

        assertThat(MetricsCalculator.aucLabels(plays, List.of(cycle(999)), 1, 2)).containsExactly(0);
    }

    private static BacktestModels.Cycle cycle(long playOrder) {
        return new BacktestModels.Cycle(null, playOrder, 1, 1, 1, 0, "S3_BACKFILL");
    }

    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return new BacktestModels.PlayRow(1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, null, true, "S3_BACKFILL", null, null, null);
    }
}
