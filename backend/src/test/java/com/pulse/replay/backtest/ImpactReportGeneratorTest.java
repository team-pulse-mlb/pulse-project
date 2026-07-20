package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
                .contains("AUC(상태 신호)")
                .contains("## 상위 10 진입·이탈\n\n- 없음");
    }

    @Test
    void 상위_N_진입과_이탈을_분리해_출력한다() throws IOException {
        BacktestProperties options = new BacktestProperties(
                "6", null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1),
                List.of(), List.of(), tempDir.toString(), null, 15, 25, 1, 2, 2,
                0.7, 0.02, 5, 2);
        ScoringProperties scoring = new ScoringConstantsLoader().loadBaseline("6");
        List<BacktestModels.ReplayResult> baseline = List.of(
                replayResult(1, 40),
                replayResult(2, 30),
                replayResult(3, 20),
                replayResult(4, 10));
        List<BacktestModels.ReplayResult> candidate = List.of(
                replayResult(1, 40),
                replayResult(2, 15),
                replayResult(3, 35),
                replayResult(4, 10));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ImpactReportGenerator.Paths paths = new ImpactReportGenerator(objectMapper).write(
                options, scoring, scoring, baseline, candidate);

        JsonNode topChanges = objectMapper.readTree(paths.json().toFile()).get("topChanges");
        assertThat(topChanges.size()).isEqualTo(2);
        assertThat(topChanges.get(0).get("gameId").asLong()).isEqualTo(3);
        assertThat(topChanges.get(0).get("direction").asText()).isEqualTo("진입");
        assertThat(topChanges.get(1).get("gameId").asLong()).isEqualTo(2);
        assertThat(topChanges.get(1).get("direction").asText()).isEqualTo("이탈");
        assertThat(Files.readString(paths.markdown()))
                .contains("## 상위 2 진입·이탈")
                .contains("| 3 | 원정3@홈3 | 진입 |")
                .contains("| 2 | 원정2@홈2 | 이탈 |");
    }

    private static BacktestModels.ReplayResult replayResult() {
        return replayResult(1, 10);
    }

    private static BacktestModels.ReplayResult replayResult(long gameId, double peak) {
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                gameId, start, "FINAL", false, 1L, 2L, 1, 0, 9, null, null,
                "홈" + gameId, "원정" + gameId);
        List<BacktestModels.PlayRow> plays = List.of(
                play(gameId, 1, 0, 0, false),
                play(gameId, 2, 1, 0, true),
                play(gameId, 3, 1, 0, false));
        List<BacktestModels.Cycle> cycles = List.of(
                new BacktestModels.Cycle(start, 1, 10, 8, peak, 0, "OPERATIONAL", Map.of()),
                new BacktestModels.Cycle(start.plusSeconds(1), 2, 2, 1, 2, 1, "OPERATIONAL", Map.of()));
        BacktestModels.GameData data = new BacktestModels.GameData(game, plays, null, null, List.of());
        return new BacktestModels.ReplayResult(data, cycles, 0);
    }

    private static BacktestModels.PlayRow play(
            long gameId,
            long order,
            int home,
            int away,
            boolean scoring
    ) {
        return new BacktestModels.PlayRow(
                gameId, order, null, 1, "TOP", home, away, scoring, scoring ? 1 : null,
                null, null, null, Instant.parse("2026-06-01T00:00:00Z"), false,
                "OPERATIONAL", null, null, null);
    }
}
