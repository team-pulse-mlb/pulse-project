package com.pulse.poller.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 시뮬레이션 데이터 소스를 실제 생성자 자동 주입 경로로 생성하는지 검증한다. */
class SimulationBeanConstructionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("pulse.simulation.enabled=true")
            .withUserConfiguration(SimulationBaseballDataSource.class)
            .withBean(SimulationProperties.class, () -> new SimulationProperties(
                    true,
                    1L,
                    9_000_000_001L,
                    List.of(),
                    20.0,
                    Duration.ZERO,
                    Duration.ofSeconds(20),
                    "SURGE",
                    null,
                    null,
                    1000
            ))
            .withBean(GameRepository.class, () -> mock(GameRepository.class))
            .withBean(PlayRepository.class, () -> mock(PlayRepository.class))
            .withBean(SimulationArchiveLoader.class, () -> mock(SimulationArchiveLoader.class));

    @Test
    @DisplayName("시뮬레이션 데이터 소스를 생성자 자동 주입으로 생성한다")
    void shouldInstantiateSimulationDataSourceByConstructorInjection() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SimulationBaseballDataSource.class);
        });
    }
}
