package com.pulse.replay.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignalDumpWriterTest {
    private static final String HEADER = "game_id,play_order,observed_at,source,inning,inning_type,outs,balls,"
            + "strikes,runner_on_first,runner_on_second,runner_on_third,home_score,away_score,label,"
            + "late_or_extra,score_gap,pressure,count_pressure,early_slugfest,recent_score,lead_change,"
            + "big_inning,base_score,state_score,watch_score";

    @TempDir
    Path tempDir;

    @Test
    void cycle별_신호와_긴장_이벤트_라벨을_CSV로_기록한다() throws IOException {
        Path outputPath = tempDir.resolve("nested/signals.csv");
        Instant start = Instant.parse("2026-07-01T00:00:00Z");
        BacktestModels.GameRow game = new BacktestModels.GameRow(
                101, start, "FINAL", false, 1L, 2L, 1, 0, 9, null, null, "홈", "원정");
        List<BacktestModels.PlayRow> plays = List.of(
                play(1, 0, 0, false, 1, 2, 1, true, false, null),
                play(2, 1, 0, true, 2, 0, 0, false, true, false),
                play(3, 1, 0, false, null, null, null, null, null, null));
        List<BacktestModels.Cycle> cycles = List.of(
                new BacktestModels.Cycle(
                        start, 1, 10.0, 4.0, 20.0, 0, "OPERATIONAL",
                        Map.of("late_or_extra", 1.5, "score_gap", 2.5)),
                new BacktestModels.Cycle(
                        start.plusSeconds(1), 2, 11.0, 5.0, 21.0, 1, "OPERATIONAL",
                        Map.of("pressure", 3.0)));
        BacktestModels.GameData data = new BacktestModels.GameData(game, plays, null, null, List.of());

        new SignalDumpWriter().write(
                outputPath,
                List.of(new BacktestModels.ReplayResult(data, cycles, 0)),
                1,
                2);

        List<String> lines = Files.readAllLines(outputPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(HEADER);

        String[] first = lines.get(1).split(",", -1);
        assertThat(first[14]).isEqualTo("1");
        assertThat(first[9]).isEqualTo("1");
        assertThat(first[10]).isEqualTo("0");
        assertThat(first[11]).isEmpty();
        assertThat(first[20]).isEqualTo("0.0");

        String[] second = lines.get(2).split(",", -1);
        assertThat(second[14]).isEqualTo("0");
        assertThat(second[9]).isEqualTo("0");
        assertThat(second[10]).isEqualTo("1");
        assertThat(second[11]).isEqualTo("0");
    }

    private static BacktestModels.PlayRow play(
            long order,
            int home,
            int away,
            boolean scoring,
            Integer outs,
            Integer balls,
            Integer strikes,
            Boolean runnerOnFirst,
            Boolean runnerOnSecond,
            Boolean runnerOnThird
    ) {
        return new BacktestModels.PlayRow(
                101, order, null, 8, "BOTTOM", home, away, scoring, scoring ? 1 : null,
                outs, balls, strikes, Instant.parse("2026-07-01T00:00:00Z"), false,
                "OPERATIONAL", runnerOnFirst, runnerOnSecond, runnerOnThird);
    }
}
