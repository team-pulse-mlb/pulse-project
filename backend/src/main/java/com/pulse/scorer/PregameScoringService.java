package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.OddsSnapshot;
import com.pulse.domain.OddsSnapshotRepository;
import com.pulse.domain.PlayerSeasonStat;
import com.pulse.domain.PlayerSeasonStatId;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.Standing;
import com.pulse.domain.StandingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PregameScoringService {

    private static final String SOURCE_ODDS_PREGAME_FINAL = "ODDS_PREGAME_FINAL";
    private static final String SOURCE_ODDS_FIRST_SEEN = "ODDS_FIRST_SEEN";
    private static final String SOURCE_WIN_PERCENT = "WIN_PERCENT";
    private static final String SOURCE_MISSING = "MISSING";

    private final GameRepository gameRepository;
    private final LineupRepository lineupRepository;
    private final OddsSnapshotRepository oddsSnapshotRepository;
    private final StandingRepository standingRepository;
    private final PlayerSeasonStatRepository playerSeasonStatRepository;
    private final ScoringProperties props;

    @Transactional
    public void handle(ScoreTask task) {
        Game game = gameRepository.findById(task.gameId()).orElse(null);
        if (game == null) {
            log.debug("예정 점수 계산 대상 경기 없음: {}", task.gameId());
            return;
        }

        Instant observedAt = task.observedAt() == null ? Instant.now() : task.observedAt();
        int season = seasonOf(game, observedAt);
        Calculation calculation = calculate(game, season);
        int pregameScore = clampScore(calculation.totalScore());

        game.setPregameScore(pregameScore);
        game.setPregameInputs(inputs(observedAt, season, pregameScore, calculation));
        gameRepository.save(game);
        log.debug("예정 점수 계산 gameId={} pregameScore={}", game.getId(), pregameScore);
    }

    private Calculation calculate(Game game, int season) {
        ComponentScore closeness = closeness(game);
        ComponentScore matchup = starterMatchup(game, season);
        ComponentScore contention = contention(game);
        return new Calculation(closeness, matchup, contention);
    }

    private ComponentScore closeness(Game game) {
        Optional<OddsChoice> oddsChoice = preferredOdds(game.getId());
        if (oddsChoice.isPresent()) {
            OddsChoice choice = oddsChoice.get();
            List<Double> probabilities = choice.snapshots().stream()
                    .map(this::normalizedHomeProbability)
                    .flatMap(Optional::stream)
                    .sorted()
                    .toList();
            if (!probabilities.isEmpty()) {
                double median = median(probabilities);
                double score = props.pregame().closenessMax()
                        * Math.max(0, 1 - Math.abs(median - 0.5)
                        / props.pregame().closenessImpliedProbabilitySpan());
                return new ComponentScore(score, choice.source(), Map.of("homeProbability", round(median)));
            }
        }

        Optional<Double> homeWinPercent = winPercent(game.getHomeTeamId());
        Optional<Double> awayWinPercent = winPercent(game.getAwayTeamId());
        if (homeWinPercent.isPresent() && awayWinPercent.isPresent()) {
            double gap = Math.abs(homeWinPercent.get() - awayWinPercent.get());
            double score = props.pregame().closenessMax()
                    * Math.max(0, 1 - gap / props.pregame().closenessWinPercentSpan());
            return new ComponentScore(score, SOURCE_WIN_PERCENT, Map.of("winPercentGap", round(gap)));
        }
        return new ComponentScore(0, SOURCE_MISSING, Map.of());
    }

    private Optional<OddsChoice> preferredOdds(Long gameId) {
        List<OddsSnapshot> pregameFinal = oddsSnapshotRepository.findByGameIdAndSnapshotType(
                gameId, OddsSnapshot.SNAPSHOT_PREGAME_FINAL);
        if (!pregameFinal.isEmpty()) {
            return Optional.of(new OddsChoice(SOURCE_ODDS_PREGAME_FINAL, pregameFinal));
        }

        List<OddsSnapshot> firstSeen = oddsSnapshotRepository.findByGameIdAndSnapshotType(
                gameId, OddsSnapshot.SNAPSHOT_FIRST_SEEN);
        if (!firstSeen.isEmpty()) {
            return Optional.of(new OddsChoice(SOURCE_ODDS_FIRST_SEEN, firstSeen));
        }
        return Optional.empty();
    }

    private Optional<Double> normalizedHomeProbability(OddsSnapshot snapshot) {
        if (snapshot.getMoneylineHomeOdds() == null || snapshot.getMoneylineAwayOdds() == null) {
            return Optional.empty();
        }
        double home = impliedProbability(snapshot.getMoneylineHomeOdds());
        double away = impliedProbability(snapshot.getMoneylineAwayOdds());
        double total = home + away;
        if (total <= 0) {
            return Optional.empty();
        }
        return Optional.of(home / total);
    }

    private ComponentScore starterMatchup(Game game, int season) {
        List<Lineup> pitchers = lineupRepository.findByGameIdAndIsProbablePitcherTrue(game.getId());
        List<Long> pitcherIds = new ArrayList<>();
        double score = 0;
        for (Lineup pitcher : pitchers) {
            pitcherIds.add(pitcher.getPlayerId());
            score += playerSeasonStatRepository.findById(new PlayerSeasonStatId(season, pitcher.getPlayerId()))
                    .map(PlayerSeasonStat::getPitchingEra)
                    .map(BigDecimal::doubleValue)
                    .map(this::starterScore)
                    .orElse(0.0);
        }
        return new ComponentScore(score, "PROBABLE_PITCHERS", Map.of("playerIds", pitcherIds));
    }

    private double starterScore(double era) {
        double ratio = (props.pregame().matchupEraBaseline() - era) / props.pregame().matchupEraSpan();
        return props.pregame().matchupPerStarterMax() * clamp01(ratio);
    }

    private ComponentScore contention(Game game) {
        Optional<Boolean> homeContending = contending(game.getHomeTeamId());
        Optional<Boolean> awayContending = contending(game.getAwayTeamId());
        if (homeContending.isEmpty() || awayContending.isEmpty()) {
            return new ComponentScore(0, SOURCE_MISSING, Map.of());
        }

        boolean home = homeContending.get();
        boolean away = awayContending.get();
        if (home && away) {
            return new ComponentScore(props.pregame().contentionBoth(), "BOTH_CONTENDING", Map.of());
        }
        if (home || away) {
            return new ComponentScore(props.pregame().contentionOne(), "ONE_CONTENDING", Map.of());
        }
        return new ComponentScore(0, "NOT_CONTENDING", Map.of());
    }

    private Optional<Boolean> contending(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId)
                .map(Standing::getPlayoffPercent)
                .map(BigDecimal::doubleValue)
                .map(value -> value >= props.importance().contentionMinPercent()
                        && value <= props.importance().contentionMaxPercent());
    }

    private Optional<Double> winPercent(Long teamId) {
        if (teamId == null) {
            return Optional.empty();
        }
        return standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId)
                .map(Standing::getWinPercent)
                .map(BigDecimal::doubleValue);
    }

    private Map<String, Object> inputs(
            Instant observedAt,
            int season,
            int pregameScore,
            Calculation calculation
    ) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("computedAt", observedAt.toString());
        inputs.put("season", season);
        inputs.put("pregameScore", pregameScore);
        inputs.put("components", Map.of(
                "closeness", componentInput(calculation.closeness()),
                "starterMatchup", componentInput(calculation.matchup()),
                "contention", componentInput(calculation.contention())
        ));
        return inputs;
    }

    private static Map<String, Object> componentInput(ComponentScore score) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("score", round(score.score()));
        input.put("source", score.source());
        input.putAll(score.details());
        return input;
    }

    private static int seasonOf(Game game, Instant observedAt) {
        Instant base = game.getStartTime() == null ? observedAt : game.getStartTime();
        return base.atZone(ZoneOffset.UTC).getYear();
    }

    private static int clampScore(double value) {
        return (int) Math.round(Math.max(0, Math.min(100, value)));
    }

    private static double impliedProbability(int moneyline) {
        if (moneyline < 0) {
            return -moneyline / (-moneyline + 100.0);
        }
        return 100.0 / (moneyline + 100.0);
    }

    private static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private record OddsChoice(String source, List<OddsSnapshot> snapshots) {
    }

    private record ComponentScore(double score, String source, Map<String, Object> details) {
    }

    private record Calculation(ComponentScore closeness, ComponentScore matchup, ComponentScore contention) {

        private double totalScore() {
            return closeness.score() + matchup.score() + contention.score();
        }
    }
}
