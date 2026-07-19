package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static com.pulse.replay.backtest.BacktestModels.PlayRow;
import static com.pulse.replay.backtest.BacktestModels.ReplayResult;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SignalDumpWriter {
    private static final List<String> SIGNAL_KEYS = List.of(
            "late_or_extra",
            "score_gap",
            "pressure",
            "count_pressure",
            "early_slugfest",
            "recent_score",
            "lead_change",
            "big_inning");
    private static final List<String> HEADER = List.of(
            "game_id",
            "play_order",
            "observed_at",
            "source",
            "inning",
            "inning_type",
            "outs",
            "balls",
            "strikes",
            "runner_on_first",
            "runner_on_second",
            "runner_on_third",
            "home_score",
            "away_score",
            "label",
            "late_or_extra",
            "score_gap",
            "pressure",
            "count_pressure",
            "early_slugfest",
            "recent_score",
            "lead_change",
            "big_inning",
            "base_score",
            "state_score",
            "watch_score");

    public void write(
            Path outputPath,
            List<ReplayResult> results,
            int aucHorizonPlays,
            int tensionScoreGapMax
    ) throws IOException {
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            writeRow(writer, HEADER);
            for (ReplayResult result : results) {
                List<Integer> labels = MetricsCalculator.aucLabels(
                        result.data().plays(),
                        result.cycles(),
                        aucHorizonPlays,
                        tensionScoreGapMax);
                for (int index = 0; index < result.cycles().size(); index++) {
                    writeCycle(writer, result, result.cycles().get(index), labels.get(index));
                }
            }
        }
    }

    private static void writeCycle(
            BufferedWriter writer,
            ReplayResult result,
            Cycle cycle,
            int label
    ) throws IOException {
        PlayRow play = findPlay(result.data().plays(), cycle.playOrder());
        List<Object> values = new ArrayList<>();
        values.add(result.data().game().gameId());
        values.add(cycle.playOrder());
        values.add(cycle.computedAt());
        values.add(cycle.source());
        values.add(play == null ? null : play.inning());
        values.add(play == null ? null : play.inningType());
        values.add(play == null ? null : play.outs());
        values.add(play == null ? null : play.balls());
        values.add(play == null ? null : play.strikes());
        values.add(play == null ? null : booleanValue(play.runnerOnFirst()));
        values.add(play == null ? null : booleanValue(play.runnerOnSecond()));
        values.add(play == null ? null : booleanValue(play.runnerOnThird()));
        values.add(play == null ? null : play.homeScore());
        values.add(play == null ? null : play.awayScore());
        values.add(label);
        Map<String, Double> signals = cycle.signals() == null ? Map.of() : cycle.signals();
        SIGNAL_KEYS.forEach(key -> values.add(signals.getOrDefault(key, 0.0)));
        values.add(cycle.baseScore());
        values.add(cycle.stateScore());
        values.add(cycle.watchScore());
        writeRow(writer, values);
    }

    private static PlayRow findPlay(List<PlayRow> plays, long playOrder) {
        return plays.stream()
                .filter(play -> play.playOrder() == playOrder)
                .findFirst()
                .orElse(null);
    }

    private static Integer booleanValue(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    private static void writeRow(BufferedWriter writer, List<?> values) throws IOException {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                writer.write(',');
            }
            writer.write(escape(values.get(index)));
        }
        writer.newLine();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.contains(",") && !text.contains("\"") && !text.contains("\n") && !text.contains("\r")) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
