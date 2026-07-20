package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pulse.common.config.ScoringProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImpactReportGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("기준 알림이 0건이면 후보 알림을 절대 상한으로만 판정한다")
    void usesAbsoluteLimitWhenBaselineHasNoAlerts() {
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(0, 3, 5, 2)).isFalse();
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(0, 6, 5, 2)).isTrue();
    }

    @Test
    @DisplayName("기준 알림이 있으면 후보 알림의 비율 상한도 판정한다")
    void usesRatioLimitWhenBaselineHasAlerts() {
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(1, 3, 5, 2)).isTrue();
        assertThat(ImpactReportGenerator.exceedsDailyAlertLimit(2, 3, 5, 2)).isFalse();
    }

    @Test
    void 상태_AUC를_JSON과_마크다운에_출력한다() throws IOException {
        BacktestProperties options = new BacktestProperties(
                "6", null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                List.of(), List.of(), tempDir.toString(), null, 15, 25, 1, 2, 10,
                0.7, 0.02, 5, 2);
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        BacktestModels.ReplayResult result = replayResult();

        ImpactReportGenerator.Paths paths = new ImpactReportGenerator(
                new ObjectMapper().registerModule(new JavaTimeModule())).write(
                options, scoring, scoring, List.of(result), List.of(result));

        assertThat(Files.readString(paths.json())).contains("\"stateAuc\"");
        assertThat(Files.readString(paths.markdown()))
                .contains("AUC(전체 신호)")
                .contains("AUC(상태 신호)");
    }

    private static BacktestModels.ReplayResult replayResult() {
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                1, start, "FINAL", false, 1L, 2L, 1, 0, 9, null, null, "홈", "원정");
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 0, 0, false),
                play(2, 1, 0, true),
                play(3, 1, 0, false));
        List<BacktestModels.Cycle> cycles = List.of(
                new BacktestModels.Cycle(start, 1, 10, 8, 10, 0, "OPERATIONAL", Map.of()),
                new BacktestModels.Cycle(start.plusSeconds(1), 2, 2, 1, 2, 1, "OPERATIONAL", Map.of()));
        BacktestModels.GameData data = new BacktestModels.GameData(game, plays, null, null, List.of());
        return new BacktestModels.ReplayResult(data, cycles, 0);
    }

    private static BacktestModels.PlayRow play(long order, int home, int away, boolean scoring) {
        return new BacktestModels.PlayRow(
                1, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, Instant.parse("2026-06-01T00:00:00Z"), false,
                "OPERATIONAL", null, null, null);
    }
}
