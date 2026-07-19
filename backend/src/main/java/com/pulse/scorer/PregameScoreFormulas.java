package com.pulse.scorer;

import com.pulse.common.config.ScoringProperties;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 경기 전 점수 구성 요소의 공통 계산식.
 */
public final class PregameScoreFormulas {

    private final ScoringProperties properties;

    public PregameScoreFormulas(ScoringProperties properties) {
        this.properties = properties;
    }

    public double impliedProbability(int moneyline) {
        if (moneyline < 0) {
            return -moneyline / (-moneyline + 100.0);
        }
        return 100.0 / (moneyline + 100.0);
    }

    public Optional<Double> normalizedHomeProbability(Integer homeOdds, Integer awayOdds) {
        if (homeOdds == null || awayOdds == null) {
            return Optional.empty();
        }
        double homeProbability = impliedProbability(homeOdds);
        double awayProbability = impliedProbability(awayOdds);
        return normalizeHomeProbability(homeProbability, awayProbability);
    }

    Optional<Double> normalizeHomeProbability(double homeProbability, double awayProbability) {
        double totalProbability = homeProbability + awayProbability;
        if (totalProbability <= 0) {
            return Optional.empty();
        }
        return Optional.of(homeProbability / totalProbability);
    }

    public double median(List<Double> values) {
        List<Double> sortedValues = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sortedValues.size() / 2;
        if (sortedValues.size() % 2 == 1) {
            return sortedValues.get(middle);
        }
        return (sortedValues.get(middle - 1) + sortedValues.get(middle)) / 2.0;
    }

    public double closenessFromProbabilities(List<Double> sortedProbabilities) {
        double medianProbability = median(sortedProbabilities);
        return properties.pregame().closenessMax() * Math.max(0,
                1 - Math.abs(medianProbability - 0.5)
                        / properties.pregame().closenessImpliedProbabilitySpan());
    }

    public double closenessFromWinPercent(double homeWinPercent, double awayWinPercent) {
        double gap = Math.abs(homeWinPercent - awayWinPercent);
        return properties.pregame().closenessMax()
                * Math.max(0, 1 - gap / properties.pregame().closenessWinPercentSpan());
    }

    public Boolean contending(Double playoffPercent) {
        if (playoffPercent == null) {
            return null;
        }
        return playoffPercent >= properties.importance().contentionMinPercent()
                && playoffPercent <= properties.importance().contentionMaxPercent();
    }

    public double contentionScore(Boolean homeContending, Boolean awayContending) {
        if (homeContending == null || awayContending == null) {
            return 0;
        }
        if (homeContending && awayContending) {
            return properties.pregame().contentionBoth();
        }
        return homeContending || awayContending ? properties.pregame().contentionOne() : 0;
    }

    public double starterScoreFromEra(double era) {
        double ratio = (properties.pregame().matchupEraBaseline() - era)
                / properties.pregame().matchupEraSpan();
        return properties.pregame().matchupPerStarterMax() * clamp01(ratio);
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
