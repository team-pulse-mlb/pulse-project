package com.pulse.ranking;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import com.pulse.domain.Game;
import com.pulse.domain.Lineup;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonalizationCalculator {

    private final ScoringProperties scoringProperties;

    public int bonus(Game game, Set<Long> lineupPlayerIds, UserPreferences preferences) {
        ScoringProperties.Personalization personalization = scoringProperties.personalization();
        int bonus = 0;
        if (containsFavoriteTeam(game, preferences.favoriteTeamIds())) {
            bonus += personalization.teamBonus();
        }
        if (lineupPlayerIds.stream().anyMatch(preferences.favoritePlayerIds()::contains)) {
            bonus += personalization.playerBonus();
        }
        return Math.min(bonus, personalization.max());
    }

    private static boolean containsFavoriteTeam(Game game, Set<Long> favoriteTeamIds) {
        return favoriteTeamIds.contains(game.getHomeTeamId())
                || favoriteTeamIds.contains(game.getAwayTeamId());
    }
}
