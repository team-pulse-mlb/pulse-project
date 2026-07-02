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
 * 기본 신호(base_score) 계산기. 랭킹은 /games + /plays에서 나온 기본 신호만 사용한다.
 * (상세 신호는 상위 경기 전용이며 랭킹에 넣지 않는다 — 태그/알림 담당 파트에서 별도 구현)
 *
 * 순수 계산 로직만 두고 저장/조회는 ScoringService가 담당한다. 가중치 검증과
 * 리플레이 재계산에서 그대로 재사용한다.
 */
@Component
public class ScoreCalculator {

    private final ScoringProperties props;

    public ScoreCalculator(ScoringProperties props) {
        this.props = props;
    }

    public record Result(Map<String, Double> signals, double baseScore) {
    }

    /**
     * @param game       경기 최신 스냅샷
     * @param recentPlays 최근 play (시간순 오름차순, 최대 window 개)
     * @param now        계산 기준 시각 (시간 감쇠용)
     */
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

    /** 후반/연장 가산: 7~8회 / 9회 / 연장 */
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

    /** 점수 차 가산: 0~1점 / 2점 / 3점 */
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

    /** 최근 득점 가산: (득점당 perRun, 최대 max) × 접전 배율 × 시간 감쇠 */
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

    /** 리드 변경 가산: 최근 window 안에서 앞선 팀이 바뀜 */
    private double leadChange(List<Play> recentPlays) {
        int lastLeader = 0; // +1 홈 리드, -1 원정 리드, 0 동점
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

    /** 빅이닝 가산: 현재 이닝에 득점 play 2개 이상 */
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

    /** 카운트/아웃 가산: 풀카운트 / 2아웃 (합산 상한) */
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

    /** 초반 난타 가산: 1~3회이고 양 팀 득점 합이 기준 이상 */
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

    /** 이닝 시작/종료 같은 이벤트 구분자를 제외한 가장 최근 play */
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
