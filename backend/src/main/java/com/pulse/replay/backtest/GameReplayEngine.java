package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static com.pulse.replay.backtest.BacktestModels.GameData;
import static com.pulse.replay.backtest.BacktestModels.GameRow;
import static com.pulse.replay.backtest.BacktestModels.PlayRow;
import static com.pulse.replay.backtest.BacktestModels.StandingRow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import com.pulse.scorer.ImportanceCalculator;
import com.pulse.scorer.PregameScoreFormulas;
import com.pulse.scorer.ScoreCalculator;
import com.pulse.scorer.ScoringInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GameReplayEngine {
    private static final Logger log = LoggerFactory.getLogger(GameReplayEngine.class);
    private static final Set<String> STATE_SIGNAL_KEYS = Set.of(
            "late_or_extra",
            "score_gap",
            "pressure",
            "count_pressure",
            "early_slugfest");

    private final BacktestProperties options;
    private final ObjectMapper objectMapper;

    public GameReplayEngine(BacktestProperties options, ObjectMapper objectMapper) {
        this.options = options;
        this.objectMapper = objectMapper;
    }

    public List<Cycle> replay(GameData data, ScoringProperties properties) {
        List<PlayRow> rows = data.plays().stream().sorted(Comparator.comparingLong(PlayRow::playOrder)).toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        boolean backfillOnly = rows.stream().allMatch(row -> "S3_BACKFILL".equals(row.source()));
        return backfillOnly ? replayBackfill(data, properties, rows) : replayTimed(data, properties, rows);
    }

    private List<Cycle> replayBackfill(GameData data, ScoringProperties properties, List<PlayRow> rows) {
        List<Cycle> cycles = new ArrayList<>();
        ScoreCalculator calculator = new ScoreCalculator(properties);
        double importance = importance(data, properties);
        double pregameScore = pregame(data, properties);
        for (int index = 0; index < rows.size(); index++) {
            PlayRow current = rows.get(index);
            int from = Math.max(0, index + 1 - properties.leadChange().windowPlays());
            List<Play> window = rows.subList(from, index + 1).stream().map(this::play).toList();
            int seed = from == 0 ? 0 : leader(rows.get(from - 1));
            ScoreCalculator.Result original = calculator.calculate(new ScoringInput(
                    game(data.game(), current), window, situation(current), seed, Instant.EPOCH,
                    importance, pregameScore));
            Map<String, Double> signals = new LinkedHashMap<>(original.signals());
            signals.put("recent_score", approximateRecent(rows, index, properties));
            signals.put("lead_change", approximateLead(rows, index, properties));
            double base = signals.values().stream().mapToDouble(Double::doubleValue).sum();
            double state = stateScore(signals);
            double watchScore = calculator.calculateWatchScore(base, importance, original.pregameBonus());
            cycles.add(cycle(current, index, base, state, watchScore, null, signals));
        }
        return cycles;
    }

    private List<Cycle> replayTimed(GameData data, ScoringProperties properties, List<PlayRow> rows) {
        List<Cycle> cycles = new ArrayList<>();
        List<PlayRow> observed = new ArrayList<>();
        ScoreCalculator calculator = new ScoreCalculator(properties);
        double importance = importance(data, properties);
        double pregameScore = pregame(data, properties);
        int index = 0;
        while (index < rows.size()) {
            PlayRow first = rows.get(index);
            if (first.observedAt() == null) {
                index++;
                continue;
            }
            int next = index;
            while (next < rows.size() && first.observedAt().equals(rows.get(next).observedAt())) {
                next++;
            }
            observed.addAll(rows.subList(index, next));
            observed.sort(Comparator.comparingLong(PlayRow::playOrder));
            PlayRow current = observed.get(observed.size() - 1);
            int from = Math.max(0, observed.size() - properties.leadChange().windowPlays());
            List<Play> window = observed.subList(from, observed.size()).stream().map(this::play).toList();
            int seed = from == 0 ? 0 : leader(observed.get(from - 1));
            ScoreCalculator.Result result = calculator.calculate(new ScoringInput(
                    game(data.game(), current), window, situation(current), seed, first.observedAt(),
                    importance, pregameScore));
            cycles.add(cycle(
                    current,
                    observed.size() - 1,
                    result.baseScore(),
                    stateScore(result.signals()),
                    result.watchScore(),
                    first.observedAt(),
                    result.signals()));
            index = next;
        }
        return cycles;
    }

    private Cycle cycle(
            PlayRow row,
            int index,
            double base,
            double state,
            double watchScore,
            Instant at,
            Map<String, Double> signals
    ) {
        return new Cycle(at, row.playOrder(), base, state, watchScore, index, row.source(), signals);
    }

    private static double stateScore(Map<String, Double> signals) {
        return STATE_SIGNAL_KEYS.stream()
                .mapToDouble(key -> signals.getOrDefault(key, 0.0))
                .sum();
    }

    double approximateRecent(List<PlayRow> rows, int current, ScoringProperties properties) {
        double total = 0;
        double budget = properties.recentScore().baseBudget();
        for (int index = current; index >= 0 && current - index < options.backfillRecentScoreWindowPlays() && budget > 0; index--) {
            PlayRow row = rows.get(index);
            if (!Boolean.TRUE.equals(row.scoringPlay())) {
                continue;
            }
            int runs = row.scoreValue() == null ? 1 : row.scoreValue();
            double base = Math.min(runs * (double) properties.recentScore().perRun(), budget);
            budget -= base;
            int gap = Math.min(3, Math.abs(value(row.homeScore()) - value(row.awayScore())));
            double decay = Math.max(0, 1.0 - (current - index) / (double) options.backfillRecentScoreWindowPlays());
            total += base * properties.recentScore().multiplierFor(gap) * decay;
        }
        return total;
    }

    double approximateLead(List<PlayRow> rows, int current, ScoringProperties properties) {
        int from = Math.max(0, current + 1 - options.backfillLeadChangeWindowPlays());
        int previous = from == 0 ? 0 : leader(rows.get(from - 1));
        for (int index = from; index <= current; index++) {
            int leader = leader(rows.get(index));
            if (leader != 0) {
                if (previous != 0 && leader != previous) {
                    return properties.leadChange().bonus();
                }
                previous = leader;
            }
        }
        return 0;
    }

    private double importance(GameData data, ScoringProperties properties) {
        return ImportanceCalculator.multiplier(
                properties.importance(),
                data.game().postseason(),
                data.homeStanding() == null ? null : data.homeStanding().playoffPercent(),
                data.awayStanding() == null ? null : data.awayStanding().playoffPercent());
    }

    private double pregame(GameData data, ScoringProperties properties) {
        PregameScoreFormulas formulas = new PregameScoreFormulas(properties);
        double closeness = oddsCloseness(data, formulas);
        if (Double.isNaN(closeness)) {
            closeness = winPercentCloseness(data, formulas);
        }
        double contention = contentionScore(data, formulas);
        double starter = starterScore(data.game().gameId(), data.game().pregameInputs());
        return Math.max(0, Math.min(100, closeness + contention + starter));
    }

    private double oddsCloseness(GameData data, PregameScoreFormulas formulas) {
        List<Double> probabilities = data.odds().stream()
                .map(odds -> formulas.normalizedHomeProbability(odds.homeOdds(), odds.awayOdds()))
                .flatMap(Optional::stream)
                .sorted().toList();
        if (probabilities.isEmpty()) {
            return Double.NaN;
        }
        return formulas.closenessFromProbabilities(probabilities);
    }

    private double winPercentCloseness(GameData data, PregameScoreFormulas formulas) {
        if (data.homeStanding() == null || data.awayStanding() == null
                || data.homeStanding().winPercent() == null || data.awayStanding().winPercent() == null) {
            return 0;
        }
        return formulas.closenessFromWinPercent(
                data.homeStanding().winPercent().doubleValue(),
                data.awayStanding().winPercent().doubleValue());
    }

    private double contentionScore(GameData data, PregameScoreFormulas formulas) {
        Boolean home = contending(data.homeStanding(), formulas);
        Boolean away = contending(data.awayStanding(), formulas);
        return formulas.contentionScore(home, away);
    }

    double starterScore(long gameId, String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            JsonNode node = objectMapper.readTree(json).path("components").path("starterMatchup").path("score");
            return node.isNumber() ? node.doubleValue() : 0;
        } catch (Exception exception) {
            log.warn("경기 전 선발 점수 입력 JSON 파싱 실패 gameId={}", gameId, exception);
            return 0;
        }
    }

    private static Boolean contending(StandingRow row, PregameScoreFormulas formulas) {
        if (row == null || row.playoffPercent() == null) {
            return null;
        }
        return formulas.contending(row.playoffPercent().doubleValue());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static int leader(PlayRow row) {
        return row.homeScore() == null || row.awayScore() == null
                ? 0
                : Integer.signum(row.homeScore() - row.awayScore());
    }

    private static ScoreTask.Situation situation(PlayRow row) {
        return ScoreTask.Situation.of(
                row.outs(),
                row.balls(),
                row.strikes(),
                row.runnerOnFirst(),
                row.runnerOnSecond(),
                row.runnerOnThird());
    }

    private static Game game(GameRow base, PlayRow row) {
        Game game = new Game();
        game.setId(base.gameId());
        game.setStatus(base.status());
        game.setPostseason(base.postseason());
        game.setHomeTeamId(base.homeTeamId());
        game.setAwayTeamId(base.awayTeamId());
        game.setPeriod(row.inning());
        game.setHomeRuns(row.homeScore());
        game.setAwayRuns(row.awayScore());
        return game;
    }

    private Play play(PlayRow row) {
        Play play = new Play();
        play.setGameId(row.gameId());
        play.setPlayOrder(row.playOrder());
        play.setType(row.type());
        play.setInning(row.inning());
        play.setInningType(row.inningType());
        play.setHomeScore(row.homeScore());
        play.setAwayScore(row.awayScore());
        play.setScoringPlay(row.scoringPlay());
        play.setScoreValue(row.scoreValue());
        play.setOuts(row.outs());
        play.setBalls(row.balls());
        play.setStrikes(row.strikes());
        play.setFetchedAt(row.observedAt() == null ? Instant.EPOCH : row.observedAt());
        play.setSource(row.source());
        play.setBackfilled(row.backfilled());
        play.setRunnerOnFirst(row.runnerOnFirst());
        play.setRunnerOnSecond(row.runnerOnSecond());
        play.setRunnerOnThird(row.runnerOnThird());
        return play;
    }
}
