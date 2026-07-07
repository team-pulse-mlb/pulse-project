package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
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

    public record Result(Map<String, Double> signals, double baseScore) {
    }

    public Result calculate(Game game, List<Play> recentPlays, Instant now) {
        Map<String, Double> signals = new LinkedHashMap<>();
        signals.put("late_or_extra", lateOrExtra(game));
        signals.put("score_gap", scoreGap(game));
        signals.put("recent_score", recentScore(recentPlays, now));
        signals.put("lead_change", leadChange(recentPlays));
        signals.put("big_inning", bigInning(game, recentPlays));
        signals.put("count_pressure", countPressure(recentPlays));
        signals.put("early_slugfest", earlySlugfest(game));

        double baseScore = signals.values().stream().mapToDouble(Double::doubleValue).sum();
        return new Result(signals, baseScore);
    }

    public double clampWatchScore(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private double lateOrExtra(Game game) {
        Integer period = game.getPeriod();
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

    private double scoreGap(Game game) {
        int gap = game.scoreGap();
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
        double budget = props.recentScore().max();

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

    private double leadChange(List<Play> recentPlays) {
        int lastLeader = 0;
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

    private double bigInning(Game game, List<Play> recentPlays) {
        Integer currentInning = game.getPeriod();
        if (currentInning == null) {
            return 0;
        }
        long scoringPlays = recentPlays.stream()
                .filter(p -> currentInning.equals(p.getInning()))
                .filter(p -> Boolean.TRUE.equals(p.getScoringPlay()))
                .count();
        return scoringPlays >= props.bigInning().minScoringPlays() ? props.bigInning().bonus() : 0;
    }

    private double countPressure(List<Play> recentPlays) {
        Play latest = latestPitchPlay(recentPlays);
        if (latest == null) {
            return 0;
        }
        double score = 0;
        if (Integer.valueOf(3).equals(latest.getBalls()) && Integer.valueOf(2).equals(latest.getStrikes())) {
            score += props.countPressure().fullCount();
        }
        if (Integer.valueOf(2).equals(latest.getOuts())) {
            score += props.countPressure().twoOuts();
        }
        return Math.min(score, props.countPressure().max());
    }

    private double earlySlugfest(Game game) {
        Integer period = game.getPeriod();
        if (period == null || period > props.earlySlugfest().maxInning()) {
            return 0;
        }
        return game.totalRuns() >= props.earlySlugfest().minTotalRuns() ? props.earlySlugfest().bonus() : 0;
    }

    private int gapOf(Play play) {
        int home = play.getHomeScore() == null ? 0 : play.getHomeScore();
        int away = play.getAwayScore() == null ? 0 : play.getAwayScore();
        return Math.abs(home - away);
    }

    private Play latestPitchPlay(List<Play> recentPlays) {
        for (int i = recentPlays.size() - 1; i >= 0; i--) {
            Play play = recentPlays.get(i);
            String type = play.getType();
            if (type != null && (type.startsWith("Start") || type.startsWith("End"))) {
                continue;
            }
            return play;
        }
        return null;
    }
}
