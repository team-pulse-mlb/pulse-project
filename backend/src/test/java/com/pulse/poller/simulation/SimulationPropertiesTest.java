package com.pulse.poller.simulation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SimulationPropertiesTest {
    @Test
    void 지원하지_않는_배속은_거부한다() {
        assertThatThrownBy(() -> new SimulationProperties(true, 1L, 2L, 2.0, Duration.ZERO, Duration.ofSeconds(20), "START", null, null, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1, 5, 20");
    }
}
