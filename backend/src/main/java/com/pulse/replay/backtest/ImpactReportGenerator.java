package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static com.pulse.replay.backtest.BacktestModels.PlayRow;
import static com.pulse.replay.backtest.BacktestModels.ReplayResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pulse.common.config.ScoringProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

public class ImpactReportGenerator {
    private final ObjectMapper objectMapper;

    public ImpactReportGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Paths write(
            BacktestProperties options,
            ScoringProperties baselineProperties,
            ScoringProperties candidateProperties,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate
    ) {
        Summary summary = summarize(options, baseline, candidate);
        String stem = "impact_v%d_vs_v%d_%s_%s".formatted(
                baselineProperties.version(),
                candidateProperties.version(),
                options.from(),
                options.to());
        Path directory = Path.of(options.outputDir());
        Path json = directory.resolve(stem + ".json");
        Path markdown = directory.resolve(stem + ".md");
        try {
            Files.createDirectories(directory);
            objectMapper.writeValue(json.toFile(), reportMap(
                    options,
                    baselineProperties,
                    candidateProperties,
                    baseline,
                    candidate,
                    summary));
            Files.writeString(markdown, markdown(
                    options,
                    baselineProperties,
                    candidateProperties,
                    baseline,
                    candidate,
                    summary));
            return new Paths(json.toAbsolutePath(), markdown.toAbsolutePath());
        } catch (IOException exception) {
            throw new IllegalStateException("백테스트 영향 리포트 생성 실패", exception);
        }
    }

    private Summary summarize(
            BacktestProperties options,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate
    ) {
        Map<Long, ReplayResult> candidates = byId(candidate);
        List<ReplayResult> common = baseline.stream()
                .filter(result -> candidates.containsKey(id(result)))
                .toList();
        List<ReplayResult> commonCandidates = common.stream()
                .map(result -> candidates.get(id(result)))
                .toList();
        List<Double> baselinePeaks = common.stream().map(ReplayResult::peak).toList();
        List<Double> candidatePeaks = commonCandidates.stream().map(ReplayResult::peak).toList();
        Double rankSpearman = MetricsCalculator.spearman(baselinePeaks, candidatePeaks);
        Double rankKendall = MetricsCalculator.kendall(baselinePeaks, candidatePeaks);
        Double baselineAuc = auc(common, options, Cycle::baseScore);
        Double candidateAuc = auc(commonCandidates, options, Cycle::baseScore);
        Double baselineStateAuc = auc(common, options, Cycle::stateScore);
        Double candidateStateAuc = auc(commonCandidates, options, Cycle::stateScore);
        Double baselineQuality = quality(common);
        Double candidateQuality = quality(commonCandidates);
        AlertStats baselineAlerts = alerts(common);
        AlertStats candidateAlerts = alerts(commonCandidates);
        List<String> guards = guards(
                options,
                rankSpearman,
                baselineAuc,
                candidateAuc,
                baselineStateAuc,
                candidateStateAuc,
                baselineQuality,
                candidateQuality,
                baselineAlerts,
                candidateAlerts);
        return new Summary(
                rankSpearman,
                rankKendall,
                baselineAuc,
                candidateAuc,
                baselineStateAuc,
                candidateStateAuc,
                baselineQuality,
                candidateQuality,
                baselineAlerts,
                candidateAlerts,
                guards);
    }

    private List<String> guards(
            BacktestProperties options,
            Double rankSpearman,
            Double baselineAuc,
            Double candidateAuc,
            Double baselineStateAuc,
            Double candidateStateAuc,
            Double baselineQuality,
            Double candidateQuality,
            AlertStats baselineAlerts,
            AlertStats candidateAlerts
    ) {
        List<String> guards = new ArrayList<>();
        if (rankSpearman != null && rankSpearman < options.guardRankCorrelationMin()) {
            guards.add("peak 순위 Spearman 임계 미달");
        }
        if (baselineAuc != null && candidateAuc != null
                && baselineAuc - candidateAuc > options.guardAucDropMax()) {
            guards.add("AUC(전체 신호) 하락 임계 초과");
        }
        if (baselineStateAuc != null && candidateStateAuc != null
                && baselineStateAuc - candidateStateAuc > options.guardAucDropMax()) {
            guards.add("AUC(상태 신호) 하락 임계 초과");
        }
        if (exceedsDailyAlertLimit(
                baselineAlerts.dailyAverage(),
                candidateAlerts.dailyAverage(),
                options.guardDailyAlertMax(),
                options.guardDailyAlertRatioMax())) {
            guards.add("일평균 알림 임계 초과");
        }
        if (baselineQuality != null && candidateQuality != null && baselineQuality - candidateQuality > 0.05) {
            guards.add("정렬 품질 하락 임계 초과");
        }
        return guards;
    }

    static boolean exceedsDailyAlertLimit(
            double baselineDailyAverage,
            double candidateDailyAverage,
            double dailyAlertMax,
            double dailyAlertRatioMax
    ) {
        if (candidateDailyAverage > dailyAlertMax) {
            return true;
        }
        return baselineDailyAverage > 0
                && candidateDailyAverage / baselineDailyAverage > dailyAlertRatioMax;
    }

    private Map<String, Object> reportMap(
            BacktestProperties options,
            ScoringProperties baselineProperties,
            ScoringProperties candidateProperties,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate,
            Summary summary
    ) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("conditions", Map.of(
                "from", options.from(),
                "to", options.to(),
                "gameCount", baseline.size(),
                "sources", sourceDistribution(baseline),
                "baselineVersion", baselineProperties.version(),
                "candidateVersion", candidateProperties.version(),
                "backfillApproximation", "최근 득점 %d plays 선형 감쇠, 리드 변경 %d plays 윈도".formatted(
                        options.backfillRecentScoreWindowPlays(),
                        options.backfillLeadChangeWindowPlays()),
                "alertLimit", "실측 시각 source는 전역 한도 적용, S3_BACKFILL은 시간 기반 알림 제어 미적용"));
        report.put("rankCorrelation", Map.of(
                "spearman", nullable(summary.rankSpearman()),
                "kendallTauB", nullable(summary.rankKendall())));
        report.put("topChanges", topChanges(options.topN(), baseline, candidate));
        report.put("auc", metric(summary.baselineAuc(), summary.candidateAuc()));
        report.put("stateAuc", metric(summary.baselineStateAuc(), summary.candidateStateAuc()));
        report.put("sortingQuality", metric(summary.baselineQuality(), summary.candidateQuality()));
        report.put("alerts", Map.of(
                "baseline", summary.baselineAlerts(),
                "candidate", summary.candidateAlerts(),
                "dailyAverageDelta",
                summary.candidateAlerts().dailyAverage() - summary.baselineAlerts().dailyAverage()));
        report.put("games", details(baseline, candidate));
        report.put("guardFlags", summary.guards());
        return report;
    }

    private String markdown(
            BacktestProperties options,
            ScoringProperties baselineProperties,
            ScoringProperties candidateProperties,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate,
            Summary summary
    ) {
        StringBuilder text = new StringBuilder("# 가중치 백테스트 영향 리포트\n\n");
        text.append("- 기간: %s ~ %s (UTC, 양끝 포함)\n".formatted(options.from(), options.to()));
        text.append("- 경기 수: %d\n".formatted(baseline.size()));
        text.append("- 상수 버전: v%d → v%d\n".formatted(
                baselineProperties.version(), candidateProperties.version()));
        text.append("- source 분포: %s\n".formatted(sourceDistribution(baseline)));
        text.append("- 근사·생략: S3_BACKFILL은 최근 득점 %d plays 선형 감쇠와 리드 변경 %d plays 윈도를 "
                .formatted(
                        options.backfillRecentScoreWindowPlays(),
                        options.backfillLeadChangeWindowPlays()));
        text.append("사용하며 시간 기반 알림 상승·쿨다운·전역 한도를 생략함. "
                + "실측 시각이 있는 source에는 전역 알림 한도를 적용함.\n\n");
        text.append("## 핵심 지표\n\n| 지표 | 기준 | 후보 | 델타 |\n|---|---:|---:|---:|\n");
        row(text, "AUC(전체 신호)", summary.baselineAuc(), summary.candidateAuc());
        row(text, "AUC(상태 신호)", summary.baselineStateAuc(), summary.candidateStateAuc());
        row(text, "정렬 품질 Spearman", summary.baselineQuality(), summary.candidateQuality());
        text.append("| peak 순위 Spearman | %s | %s | - |\n".formatted(
                format(summary.rankSpearman()), format(summary.rankSpearman())));
        text.append("| peak 순위 Kendall tau-b | %s | %s | - |\n".formatted(
                format(summary.rankKendall()), format(summary.rankKendall())));
        text.append("| 일평균 알림 | %.3f | %.3f | %.3f |\n".formatted(
                summary.baselineAlerts().dailyAverage(),
                summary.candidateAlerts().dailyAverage(),
                summary.candidateAlerts().dailyAverage() - summary.baselineAlerts().dailyAverage()));
        text.append("| 일최대 알림 | %d | %d | %d |\n\n".formatted(
                summary.baselineAlerts().dailyMax(),
                summary.candidateAlerts().dailyMax(),
                summary.candidateAlerts().dailyMax() - summary.baselineAlerts().dailyMax()));
        text.append("## 경보\n\n");
        if (summary.guards().isEmpty()) {
            text.append("- 없음\n");
        } else {
            summary.guards().forEach(flag -> text.append("- [경보] ").append(flag).append('\n'));
        }
        appendTopChanges(text, options.topN(), baseline, candidate);
        return text.toString();
    }

    private void appendTopChanges(
            StringBuilder text,
            int topN,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate
    ) {
        text.append("\n## 상위 %d 진입·이탈\n\n".formatted(topN));
        List<Map<String, Object>> changes = topChanges(topN, baseline, candidate);
        if (changes.isEmpty()) {
            text.append("- 없음\n");
            return;
        }
        text.append("| 경기 | 팀 | 변화 | 기준 peak | 후보 peak | 기준 순위 | 후보 순위 |\n");
        text.append("|---:|---|---|---:|---:|---:|---:|\n");
        changes.forEach(item -> text.append(
                "| %s | %s | %s | %.2f | %.2f | %s | %s |\n".formatted(
                        item.get("gameId"),
                        item.get("teams"),
                        item.get("direction"),
                        item.get("baselinePeak"),
                        item.get("candidatePeak"),
                        item.get("baselineRank"),
                        item.get("candidateRank"))));
    }

    private static Double auc(
            List<ReplayResult> results,
            BacktestProperties options,
            ToDoubleFunction<Cycle> score
    ) {
        List<Integer> labels = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (ReplayResult result : results) {
            labels.addAll(MetricsCalculator.aucLabels(
                    result.data().plays(),
                    result.cycles(),
                    options.aucHorizonPlays(),
                    options.tensionScoreGapMax()));
            scores.addAll(result.cycles().stream().mapToDouble(score).boxed().toList());
        }
        return MetricsCalculator.auc(labels, scores);
    }

    private static Double quality(List<ReplayResult> results) {
        return MetricsCalculator.spearman(
                results.stream().map(result -> MetricsCalculator.competitiveness(result.data())).toList(),
                results.stream().map(ReplayResult::peak).toList());
    }

    private static AlertStats alerts(List<ReplayResult> results) {
        Map<LocalDate, Integer> days = new LinkedHashMap<>();
        results.forEach(result -> days.merge(result.date(), result.alertCount(), Integer::sum));
        int total = days.values().stream().mapToInt(Integer::intValue).sum();
        double average = days.isEmpty() ? 0 : total / (double) days.size();
        int max = days.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        Map<Long, Integer> perGame = results.stream().collect(Collectors.toMap(
                ImpactReportGenerator::id,
                ReplayResult::alertCount));
        return new AlertStats(total, average, max, perGame);
    }

    private static List<Map<String, Object>> topChanges(
            int topN,
            List<ReplayResult> baseline,
            List<ReplayResult> candidate
    ) {
        Map<Long, Integer> baselineRanks = ranks(baseline);
        Map<Long, Integer> candidateRanks = ranks(candidate);
        Map<Long, ReplayResult> candidates = byId(candidate);
        List<ReplayResult> common = baseline.stream()
                .filter(result -> candidates.containsKey(id(result)))
                .toList();

        // 진입은 후보 상위 N에 새로 포함된 경기이고, 이탈은 기준 상위 N에서 제외된 경기다.
        List<Map<String, Object>> changes = new ArrayList<>();
        common.stream()
                .filter(result -> baselineRanks.get(id(result)) > topN
                        && candidateRanks.get(id(result)) <= topN)
                .sorted(Comparator.comparingInt(result -> candidateRanks.get(id(result))))
                .map(result -> topChange(
                        result, candidates.get(id(result)), baselineRanks, candidateRanks, "진입"))
                .forEach(changes::add);
        common.stream()
                .filter(result -> baselineRanks.get(id(result)) <= topN
                        && candidateRanks.get(id(result)) > topN)
                .sorted(Comparator.comparingInt(result -> baselineRanks.get(id(result))))
                .map(result -> topChange(
                        result, candidates.get(id(result)), baselineRanks, candidateRanks, "이탈"))
                .forEach(changes::add);
        return changes;
    }

    private static Map<String, Object> topChange(
            ReplayResult baseline,
            ReplayResult candidate,
            Map<Long, Integer> baselineRanks,
            Map<Long, Integer> candidateRanks,
            String direction
    ) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("gameId", id(baseline));
        item.put("teams", baseline.data().game().awayTeam() + "@" + baseline.data().game().homeTeam());
        item.put("direction", direction);
        item.put("baselinePeak", baseline.peak());
        item.put("candidatePeak", candidate.peak());
        item.put("baselineRank", baselineRanks.get(id(baseline)));
        item.put("candidateRank", candidateRanks.get(id(baseline)));
        return item;
    }

    private static List<Map<String, Object>> details(
            List<ReplayResult> baseline,
            List<ReplayResult> candidate
    ) {
        Map<Long, ReplayResult> candidates = byId(candidate);
        return baseline.stream()
                .filter(result -> candidates.containsKey(id(result)))
                .map(result -> detail(result, candidates.get(id(result))))
                .toList();
    }

    private static Map<String, Object> detail(ReplayResult baseline, ReplayResult candidate) {
        return Map.of(
                "gameId", id(baseline),
                "baselinePeak", baseline.peak(),
                "candidatePeak", candidate.peak(),
                "baselineAlerts", baseline.alertCount(),
                "candidateAlerts", candidate.alertCount());
    }

    private static Map<Long, Integer> ranks(List<ReplayResult> results) {
        List<ReplayResult> sorted = results.stream()
                .sorted(Comparator.comparingDouble(ReplayResult::peak).reversed())
                .toList();
        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (int index = 0; index < sorted.size(); index++) {
            ranks.put(id(sorted.get(index)), index + 1);
        }
        return ranks;
    }

    private static Map<Long, ReplayResult> byId(List<ReplayResult> results) {
        return results.stream().collect(Collectors.toMap(ImpactReportGenerator::id, result -> result));
    }

    private static long id(ReplayResult result) {
        return result.data().game().gameId();
    }

    private static Map<String, Long> sourceDistribution(List<ReplayResult> results) {
        return results.stream()
                .flatMap(result -> result.data().plays().stream())
                .collect(Collectors.groupingBy(
                        PlayRow::source,
                        LinkedHashMap::new,
                        Collectors.counting()));
    }

    private static Map<String, Object> metric(Double baseline, Double candidate) {
        return Map.of(
                "baseline", nullable(baseline),
                "candidate", nullable(candidate),
                "delta", baseline == null || candidate == null ? "산출 불가" : candidate - baseline);
    }

    private static Object nullable(Double value) {
        return value == null ? "산출 불가" : value;
    }

    private static String format(Double value) {
        return value == null ? "산출 불가" : "%.4f".formatted(value);
    }

    private static void row(StringBuilder text, String name, Double baseline, Double candidate) {
        String delta = baseline == null || candidate == null
                ? "산출 불가"
                : "%.4f".formatted(candidate - baseline);
        text.append("| %s | %s | %s | %s |\n".formatted(
                name,
                format(baseline),
                format(candidate),
                delta));
    }

    public record Paths(Path json, Path markdown) {
    }

    public record AlertStats(int total, double dailyAverage, int dailyMax, Map<Long, Integer> perGame) {
    }

    private record Summary(
            Double rankSpearman,
            Double rankKendall,
            Double baselineAuc,
            Double candidateAuc,
            Double baselineStateAuc,
            Double candidateStateAuc,
            Double baselineQuality,
            Double candidateQuality,
            AlertStats baselineAlerts,
            AlertStats candidateAlerts,
            List<String> guards
    ) {
    }
}
