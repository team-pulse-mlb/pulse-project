package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.Play;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 기본 신호(base_score) 계산기. 저장·조회와 분리해 S3 리플레이와 API 모두에서 재사용한다.
 */
@Component
public class ScoreCalculator {

    private final ScoringProperties props;

    public ScoreCalculator(ScoringProperties props) {
        this.props = props;
    }

    public record Result(
            Map<String, Double> signals,
            double baseScore,
            boolean fullCountIncluded,
            double importanceMultiplier,
            double pregameBonus,
            double watchScore
    ) {
        public Result(Map<String, Double> signals, double baseScore, boolean fullCountIncluded) {
            this(signals, baseScore, fullCountIncluded, 1.0, 0, baseScore);
        }
    }

    /**
     * 리플레이·백테스트 경로. situation을 최신 play의 주자/카운트 컬럼에서 복원해 계산한다.
     */
    public Result calculate(Game game, List<Play> recentPlays, Instant now) {
        return calculate(game, recentPlays, situationFrom(recentPlays), 0, now);
    }

    /**
     * 라이브 경로. poller가 전달한 ScoreTask.situation으로 압박 신호를 계산한다.
     * situation=null이면 압박 신호는 0점(null-safe)이다.
     */
    public Result calculate(Game game, List<Play> recentPlays, ScoreTask.Situation situation, Instant now) {
        return calculate(game, recentPlays, situation, 0, now);
    }

    public Result calculate(
            Game game,
            List<Play> recentPlays,
            ScoreTask.Situation situation,
            int seedLeader,
            Instant now
    ) {
        return calculate(new ScoringInput(game, recentPlays, situation, seedLeader, now, 1.0, 0));
    }

    /** 모든 점수 경로가 사용하는 단일 계산 파이프라인. */
    public Result calculate(ScoringInput input) {
        Game game = input.game();
        List<Play> recentPlays = input.recentPlays();
        ScoreTask.Situation situation = input.situation();
        Instant now = input.computedAt();
        Map<String, Double> signals = new LinkedHashMap<>();
        signals.put("late_or_extra", lateOrExtra(game, input.gameSnapshot()));
        signals.put("score_gap", scoreGap(game, input.gameSnapshot()));
        signals.put("recent_score", recentScore(recentPlays, now));
        signals.put("lead_change", leadChange(recentPlays, input.seedLeader()));
        signals.put("big_inning", bigInning(recentPlays));
        signals.put("pressure", pressure(situation));
        signals.put("count_pressure", countPressure(situation));
        signals.put("early_slugfest", earlySlugfest(game, input.gameSnapshot()));

        double baseScore = signals.values().stream().mapToDouble(Double::doubleValue).sum();
        double pregameBonus = pregameBonus(input.pregameScore());
        double watchScore = calculateWatchScore(baseScore, input.importanceMultiplier(), pregameBonus);
        return new Result(
                signals,
                baseScore,
                isFullCount(situation),
                input.importanceMultiplier(),
                pregameBonus,
                watchScore);
    }

    private static ScoreTask.Situation situationFrom(List<Play> recentPlays) {
        if (recentPlays.isEmpty()) {
            return null;
        }
        Play latest = recentPlays.get(recentPlays.size() - 1);
        return ScoreTask.Situation.of(
                latest.getOuts(),
                latest.getBalls(),
                latest.getStrikes(),
                latest.getRunnerOnFirst(),
                latest.getRunnerOnSecond(),
                latest.getRunnerOnThird()
        );
    }

    private double pressure(ScoreTask.Situation situation) {
        if (situation == null) {
            return 0;
        }
        if (situation.basesLoaded()) {
            return props.pressure().basesLoaded();
        }
        if (situation.scoringPosition()) {
            return props.pressure().scoringPosition();
        }
        return 0;
    }

    public double clampWatchScore(double value) {
        return Math.max(0, Math.min(100, value));
    }

    public double calculateWatchScore(double baseScore, double importanceMultiplier, double pregameBonus) {
        return clampWatchScore(baseScore * importanceMultiplier + pregameBonus);
    }

    private double pregameBonus(double pregameScore) {
        return Math.min(pregameScore / 10.0, props.pregameCarryoverMax());
    }

    private double lateOrExtra(Game game, ScoreTask.GameSnapshot gameSnapshot) {
        Integer period = gameSnapshot == null ? game.getPeriod() : gameSnapshot.period();
        if (period == null) {
            return 0;
        }
        if (period >= 10) {
            return props.lateInning().extra();
        }
        if (period == 9) {
            return props.lateInning().inning9();
        }
        if (period >= 7) {
            return props.lateInning().inning78();
        }
        return 0;
    }

    private double scoreGap(Game game, ScoreTask.GameSnapshot gameSnapshot) {
        int gap;
        if (gameSnapshot == null) {
            gap = game.scoreGap();
        } else {
            int homeRuns = gameSnapshot.homeRuns() == null ? 0 : gameSnapshot.homeRuns();
            int awayRuns = gameSnapshot.awayRuns() == null ? 0 : gameSnapshot.awayRuns();
            gap = Math.abs(homeRuns - awayRuns);
        }
        if (gap <= 1) {
            return props.scoreGap().gap01();
        }
        if (gap == 2) {
            return props.scoreGap().gap2();
        }
        if (gap == 3) {
            return props.scoreGap().gap3();
        }
        return 0;
    }

    private double recentScore(List<Play> recentPlays, Instant now) {
        double total = 0;
        double budget = props.recentScore().baseBudget();

        for (int i = recentPlays.size() - 1; i >= 0 && budget > 0; i--) {
            Play play = recentPlays.get(i);
            if (!Boolean.TRUE.equals(play.getScoringPlay())) {
                continue;
            }
            int runs = play.getScoreValue() == null ? 1 : play.getScoreValue();
            double base = Math.min(runs * (double) props.recentScore().perRun(), budget);
            budget -= base;

            int gapAfter = gapOf(play);
            double multiplier = props.recentScore().multiplierFor(Math.min(gapAfter, 3));
            double ageSeconds = Math.max(0, Duration.between(play.getFetchedAt(), now).getSeconds());
            double decay = Math.exp(-ageSeconds / props.recentScore().tauSeconds());

            total += base * multiplier * decay;
        }
        return total;
    }

    private double leadChange(List<Play> recentPlays, int seedLeader) {
        int lastLeader = seedLeader;
        for (Play play : recentPlays) {
            if (play.getHomeScore() == null || play.getAwayScore() == null) {
                continue;
            }
            int leader = Integer.signum(play.getHomeScore() - play.getAwayScore());
            if (leader != 0) {
                if (lastLeader != 0 && leader != lastLeader) {
                    return props.leadChange().bonus();
                }
                lastLeader = leader;
            }
        }
        return 0;
    }

    private double bigInning(List<Play> recentPlays) {
        if (recentPlays.isEmpty()) {
            return 0;
        }
        Play latest = recentPlays.get(recentPlays.size() - 1);
        long scoringPlays = recentPlays.stream()
                .filter(p -> java.util.Objects.equals(latest.getInning(), p.getInning()))
                .filter(p -> java.util.Objects.equals(latest.getInningType(), p.getInningType()))
                .filter(p -> Boolean.TRUE.equals(p.getScoringPlay()))
                .count();
        return scoringPlays >= props.bigInning().minScoringPlays() ? props.bigInning().bonus() : 0;
    }

    private double countPressure(ScoreTask.Situation situation) {
        if (situation == null) {
            return 0;
        }
        double score = 0;
        if (isFullCount(situation)) {
            score += props.countPressure().fullCount();
        }
        if (Integer.valueOf(2).equals(situation.outs())) {
            score += props.countPressure().twoOuts();
        }
        return Math.min(score, props.countPressure().max());
    }

    private static boolean isFullCount(ScoreTask.Situation situation) {
        return situation != null
                && Integer.valueOf(3).equals(situation.balls())
                && Integer.valueOf(2).equals(situation.strikes());
    }

    private double earlySlugfest(Game game, ScoreTask.GameSnapshot gameSnapshot) {
        Integer period = gameSnapshot == null ? game.getPeriod() : gameSnapshot.period();
        if (period == null || period > props.earlySlugfest().maxInning()) {
            return 0;
        }
        int totalRuns;
        if (gameSnapshot == null) {
            totalRuns = game.totalRuns();
        } else {
            int homeRuns = gameSnapshot.homeRuns() == null ? 0 : gameSnapshot.homeRuns();
            int awayRuns = gameSnapshot.awayRuns() == null ? 0 : gameSnapshot.awayRuns();
            totalRuns = homeRuns + awayRuns;
        }
        return totalRuns >= props.earlySlugfest().minTotalRuns() ? props.earlySlugfest().bonus() : 0;
    }

    private int gapOf(Play play) {
        int home = play.getHomeScore() == null ? 0 : play.getHomeScore();
        int away = play.getAwayScore() == null ? 0 : play.getAwayScore();
        return Math.abs(home - away);
    }

}
