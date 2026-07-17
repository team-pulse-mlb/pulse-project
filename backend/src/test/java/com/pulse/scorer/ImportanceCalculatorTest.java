package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pulse.common.config.ScoringProperties;
import com.pulse.domain.Game;
import com.pulse.domain.StandingRepository;
import org.junit.jupiter.api.Test;

class ImportanceCalculatorTest {

    private final ScoringProperties properties = TestScoringProperties.version5();
    private final ImportanceCalculator calculator = new ImportanceCalculator(
            mock(StandingRepository.class),
            properties
    );

    @Test
    void multiplier_shouldUseProvidedPostseasonValue() {
        Game game = new Game();
        game.setPostseason(true);

        double multiplier = calculator.multiplier(game, false);

        assertThat(multiplier).isEqualTo(1.0);
    }

    @Test
    void multiplier_shouldKeepUsingGamePostseasonValueByDefault() {
        Game game = new Game();
        game.setPostseason(true);

        double multiplier = calculator.multiplier(game);

        assertThat(multiplier).isEqualTo(properties.importance().postseason());
    }
}
