package com.pulse.scorer;

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
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class ImportanceCalculator {

    private final StandingRepository standingRepository;
    private final ScoringProperties props;

    public double multiplier(Game game) {
        ScoringProperties.Importance importance = props.importance();
        if (Boolean.TRUE.equals(game.getPostseason())) {
            return importance.postseason();
        }

        Boolean homeContending = contending(game.getHomeTeamId(), importance);
        Boolean awayContending = contending(game.getAwayTeamId(), importance);
        if (homeContending == null || awayContending == null) {
            return 1.0;
        }

        if (homeContending && awayContending) {
            return importance.bothContending();
        }
        if (homeContending || awayContending) {
            return importance.oneContending();
        }
        if (bothClearlyOut(game, importance)) {
            return importance.bothOut();
        }
        return 1.0;
    }

    private Boolean contending(Long teamId, ScoringProperties.Importance importance) {
        if (teamId == null) {
            return null;
        }
        BigDecimal playoffPercent = standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId)
                .map(Standing::getPlayoffPercent)
                .orElse(null);
        if (playoffPercent == null) {
            return null;
        }
        double value = playoffPercent.doubleValue();
        return value >= importance.contentionMinPercent() && value <= importance.contentionMaxPercent();
    }

    private boolean bothClearlyOut(Game game, ScoringProperties.Importance importance) {
        return belowContention(game.getHomeTeamId(), importance) && belowContention(game.getAwayTeamId(), importance);
    }

    private boolean belowContention(Long teamId, ScoringProperties.Importance importance) {
        if (teamId == null) {
            return false;
        }
        return standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(teamId)
                .map(Standing::getPlayoffPercent)
                .map(percent -> percent.doubleValue() < importance.contentionMinPercent())
                .orElse(false);
    }
}
