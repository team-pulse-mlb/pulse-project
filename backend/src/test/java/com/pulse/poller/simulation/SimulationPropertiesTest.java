package com.pulse.poller.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationPropertiesTest {
    @Test
    void 지원하지_않는_배속은_거부한다() {
        assertThatThrownBy(() -> new SimulationProperties(
                true, 1L, 2L, List.of(), 2.0, Duration.ZERO, Duration.ofSeconds(20),
                "START", null, null, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1, 5, 20");
    }

    @Test
    void 다중_경기_목록을_단일_경기_설정보다_우선한다() {
        SimulationProperties properties = properties(
                1L, 2L,
                List.of(new SimulationProperties.GameSpec(10L, 20L, Duration.ofMinutes(6), " surge ")),
                Duration.ofMinutes(1), "START");

        assertThat(properties.resolvedGames()).containsExactly(
                new SimulationProperties.ResolvedGameSpec(10L, 20L, Duration.ofMinutes(6), "SURGE"));
    }

    @Test
    void 다중_경기_목록이_비면_단일_경기_설정으로_해석한다() {
        SimulationProperties properties = properties(
                10L, 20L, List.of(), Duration.ofMinutes(2), " surge ");

        assertThat(properties.resolvedGames()).containsExactly(
                new SimulationProperties.ResolvedGameSpec(10L, 20L, Duration.ofMinutes(2), "SURGE"));
    }

    @Test
    void 다중_경기의_대상_ID와_누락값을_정규화하고_음수_오프셋을_허용한다() {
        SimulationProperties properties = properties(
                null, null,
                List.of(new SimulationProperties.GameSpec(10L, null, Duration.ofHours(-3), null)),
                Duration.ZERO, "START");

        assertThat(properties.resolvedGames()).containsExactly(
                new SimulationProperties.ResolvedGameSpec(10L, 9_000_000_010L, Duration.ofHours(-3), "START"));
    }

    @Test
    void 다중_경기의_대상_ID는_유일하고_원본_ID와_달라야_한다() {
        SimulationProperties duplicated = properties(
                null, null,
                List.of(
                        new SimulationProperties.GameSpec(10L, 100L, Duration.ZERO, "START"),
                        new SimulationProperties.GameSpec(20L, 100L, Duration.ZERO, "START")),
                Duration.ZERO, "START");
        SimulationProperties sameAsSource = properties(
                null, null,
                List.of(new SimulationProperties.GameSpec(10L, 10L, Duration.ZERO, "START")),
                Duration.ZERO, "START");

        assertThatThrownBy(duplicated::resolvedGames)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique");
        assertThatThrownBy(sameAsSource::resolvedGames)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void 다중_경기의_원본_ID가_없으면_중단한다() {
        SimulationProperties properties = properties(
                null, null,
                List.of(new SimulationProperties.GameSpec(null, 100L, Duration.ZERO, "START")),
                Duration.ZERO, "START");

        assertThatThrownBy(properties::resolvedGames)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source-game-id");
    }

    private static SimulationProperties properties(
            Long sourceGameId, Long targetGameId, List<SimulationProperties.GameSpec> games,
            Duration startOffset, String preset) {
        return new SimulationProperties(
                true, sourceGameId, targetGameId, games, 1.0, startOffset, Duration.ofSeconds(20),
                preset, null, null, 1000);
    }
}
