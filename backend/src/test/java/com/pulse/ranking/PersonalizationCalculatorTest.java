package com.pulse.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.config.ScoringProperties;
import com.pulse.common.user.UserPreferenceReader.UserPreferences;
import com.pulse.domain.Game;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalizationCalculatorTest {

    private final PersonalizationCalculator calculator = new PersonalizationCalculator(scoringProperties());

    @Test
    void 관심_팀과_선수는_개수와_무관하게_각각_한_번만_가산한다() {
        Game game = game(10L, 20L);
        UserPreferences preferences = new UserPreferences(
                Set.of(10L, 20L),
                Set.of(100L, 200L, 300L));

        int bonus = calculator.bonus(game, Set.of(100L, 200L), preferences);

        assertThat(bonus).isEqualTo(15);
    }

    @Test
    void 관심_팀만_일치하면_십_점을_가산한다() {
        int bonus = calculator.bonus(
                game(10L, 20L),
                Set.of(100L),
                new UserPreferences(Set.of(10L), Set.of(999L)));

        assertThat(bonus).isEqualTo(10);
    }

    @Test
    void 관심_선수만_라인업에_있으면_오_점을_가산한다() {
        int bonus = calculator.bonus(
                game(10L, 20L),
                Set.of(100L),
                new UserPreferences(Set.of(99L), Set.of(100L)));

        assertThat(bonus).isEqualTo(5);
    }

    private static Game game(long homeTeamId, long awayTeamId) {
        Game game = new Game();
        game.setHomeTeamId(homeTeamId);
        game.setAwayTeamId(awayTeamId);
        return game;
    }

    private static ScoringProperties scoringProperties() {
        return new ScoringProperties(
                6, null, null, null, null, null, null, null, null, null,
                10, new ScoringProperties.Personalization(10, 5, 15),
                null, null, null, null, null);
    }
}
