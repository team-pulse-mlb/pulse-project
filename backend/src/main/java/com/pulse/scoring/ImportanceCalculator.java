package com.pulse.scoring;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.Standing;
import com.pulse.domain.StandingRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 경기 중요도 보정 배수. 순위 일 배치의 playoff_percent로 경쟁권을 판정한다.
 * 데이터가 없으면 1.0(영향 없음)으로 둔다.
 */
@Component
@ConditionalOnProperty(prefix = "pulse.game-processor", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImportanceCalculator {

    private final StandingRepository standingRepository;
    private final ScoringProperties props;

    public double multiplier(Game game) {
        return multiplier(game, game.getPostseason());
    }

    public double multiplier(Game game, Boolean postseason) {
        ScoringProperties.Importance importance = props.importance();
        if (Boolean.TRUE.equals(postseason)) {
            return importance.postseason();
        }
        BigDecimal homePlayoffPercent = playoffPercent(game.getHomeTeamId());
        BigDecimal awayPlayoffPercent = playoffPercent(game.getAwayTeamId());
        return multiplier(importance, postseason, homePlayoffPercent, awayPlayoffPercent);
    }

    public static double multiplier(
            ScoringProperties.Importance importance,
            Boolean postseason,
            BigDecimal homePlayoffPercent,
            BigDecimal awayPlayoffPercent
    ) {
        if (Boolean.TRUE.equals(postseason)) {
            return importance.postseason();
        }

        Boolean homeContending = contending(homePlayoffPercent, importance);
        Boolean awayContending = contending(awayPlayoffPercent, importance);
        if (homeContending == null || awayContending == null) {
            return 1.0;
        }

        if (homeContending && awayContending) {
            return importance.bothContending();
        }
        if (homeContending || awayContending) {
            return importance.oneContending();
        }
        if (belowContention(homePlayoffPercent, importance)
                && belowContention(awayPlayoffPercent, importance)) {
            return importance.bothOut();
        }
        return 1.0;
    }

    private BigDecimal playoffPercent(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId)
                .map(Standing::getPlayoffPercent)
                .orElse(null);
    }

    private static Boolean contending(BigDecimal playoffPercent, ScoringProperties.Importance importance) {
        if (playoffPercent == null) {
            return null;
        }
        double value = playoffPercent.doubleValue();
        return value >= importance.contentionMinPercent() && value <= importance.contentionMaxPercent();
    }

    private static boolean belowContention(BigDecimal playoffPercent, ScoringProperties.Importance importance) {
        return playoffPercent != null && playoffPercent.doubleValue() < importance.contentionMinPercent();
    }
}
