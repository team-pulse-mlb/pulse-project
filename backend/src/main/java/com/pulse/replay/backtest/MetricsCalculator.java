package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static com.pulse.replay.backtest.BacktestModels.GameData;
import static com.pulse.replay.backtest.BacktestModels.PlayRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MetricsCalculator {
    private MetricsCalculator() {
    }

    public static Double spearman(List<Double> left, List<Double> right) {
        return pearson(ranks(left), ranks(right));
    }

    public static Double kendall(List<Double> left, List<Double> right) {
        check(left, right);
        long concordant = 0;
        long discordant = 0;
        long tiesLeft = 0;
        long tiesRight = 0;
        for (int first = 0; first < left.size(); first++) {
            for (int second = first + 1; second < left.size(); second++) {
                int leftComparison = Double.compare(left.get(first), left.get(second));
                int rightComparison = Double.compare(right.get(first), right.get(second));
                if (leftComparison == 0 && rightComparison != 0) {
                    tiesLeft++;
                } else if (leftComparison != 0 && rightComparison == 0) {
                    tiesRight++;
                } else if (leftComparison * rightComparison > 0) {
                    concordant++;
                } else if (leftComparison * rightComparison < 0) {
                    discordant++;
                }
            }
        }
        double denominator = Math.sqrt(
                (concordant + discordant + tiesLeft) * (double) (concordant + discordant + tiesRight));
        return denominator == 0 ? null : (concordant - discordant) / denominator;
    }

    public static Double auc(List<Integer> labels, List<Double> predictions) {
        if (labels.size() != predictions.size()) {
            throw new IllegalArgumentException("라벨과 예측값 개수가 다릅니다.");
        }
        long positives = labels.stream().filter(value -> value == 1).count();
        long negatives = labels.size() - positives;
        if (positives == 0 || negatives == 0) {
            return null;
        }
        double wins = 0;
        for (int positive = 0; positive < labels.size(); positive++) {
            if (labels.get(positive) != 1) {
                continue;
            }
            for (int negative = 0; negative < labels.size(); negative++) {
                if (labels.get(negative) != 0) {
                    continue;
                }
                if (predictions.get(positive) > predictions.get(negative)) {
                    wins++;
                } else if (predictions.get(positive).equals(predictions.get(negative))) {
                    wins += 0.5;
                }
            }
        }
        return wins / (positives * (double) negatives);
    }

    public static List<Integer> aucLabels(
            List<PlayRow> plays,
            List<Cycle> cycles,
            int horizon,
            int tensionScoreGapMax
    ) {
        List<PlayRow> ordered = plays.stream()
                .sorted(Comparator.comparingLong(PlayRow::playOrder))
                .toList();
        List<Integer> labels = new ArrayList<>();
        for (Cycle cycle : cycles) {
            int current = indexOf(ordered, cycle.playOrder());
            if (current < 0) {
                labels.add(0);
                continue;
            }
            int end = Math.min(ordered.size(), current + horizon + 1);
            boolean event = false;
            int leader = leader(ordered.get(current));
            for (int index = current + 1; index < end; index++) {
                PlayRow play = ordered.get(index);
                int nextLeader = leader(play);
                if ((Boolean.TRUE.equals(play.scoringPlay())
                        && Math.abs(value(play.homeScore()) - value(play.awayScore())) <= tensionScoreGapMax)
                        || (leader != 0 && nextLeader != 0 && leader != nextLeader)) {
                    event = true;
                    break;
                }
                if (nextLeader != 0) {
                    leader = nextLeader;
                }
            }
            labels.add(event ? 1 : 0);
        }
        return labels;
    }

    public static double competitiveness(GameData data) {
        List<PlayRow> plays = data.plays().stream()
                .sorted(Comparator.comparingLong(PlayRow::playOrder))
                .toList();
        int changes = 0;
        int previous = 0;
        boolean lateTie = false;
        boolean extra = false;
        for (PlayRow play : plays) {
            int leader = leader(play);
            if (previous != 0 && leader != 0 && previous != leader) {
                changes++;
            }
            if (leader != 0) {
                previous = leader;
            }
            if (play.inning() != null && play.inning() >= 8 && leader == 0) {
                lateTie = true;
            }
            if (play.inning() != null && play.inning() >= 10) {
                extra = true;
            }
        }
        int gap = Math.abs(value(data.game().homeRuns()) - value(data.game().awayRuns()));
        return changes * 2 + (lateTie ? 3 : 0) + (extra ? 3 : 0) - gap;
    }

    private static List<Double> ranks(List<Double> values) {
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble(values::get));
        List<Double> ranks = new ArrayList<>(java.util.Collections.nCopies(values.size(), 0.0));
        int start = 0;
        while (start < order.size()) {
            int end = start + 1;
            while (end < order.size() && values.get(order.get(start)).equals(values.get(order.get(end)))) {
                end++;
            }
            double rank = (start + end - 1) / 2.0 + 1;
            for (int index = start; index < end; index++) {
                ranks.set(order.get(index), rank);
            }
            start = end;
        }
        return ranks;
    }

    private static Double pearson(List<Double> left, List<Double> right) {
        check(left, right);
        if (left.size() < 2) {
            return null;
        }
        double leftMean = left.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double rightMean = right.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double numerator = 0;
        double leftDenominator = 0;
        double rightDenominator = 0;
        for (int index = 0; index < left.size(); index++) {
            double leftDifference = left.get(index) - leftMean;
            double rightDifference = right.get(index) - rightMean;
            numerator += leftDifference * rightDifference;
            leftDenominator += leftDifference * leftDifference;
            rightDenominator += rightDifference * rightDifference;
        }
        double denominator = Math.sqrt(leftDenominator * rightDenominator);
        return denominator == 0 ? null : numerator / denominator;
    }

    private static void check(List<?> left, List<?> right) {
        if (left.size() != right.size()) {
            throw new IllegalArgumentException("비교 값 개수가 다릅니다.");
        }
    }

    private static int indexOf(List<PlayRow> rows, long playOrder) {
        for (int index = 0; index < rows.size(); index++) {
            if (rows.get(index).playOrder() == playOrder) {
                return index;
            }
        }
        return -1;
    }

    private static int leader(PlayRow row) {
        if (row.homeScore() == null || row.awayScore() == null) {
            return 0;
        }
        return Integer.signum(row.homeScore() - row.awayScore());
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
