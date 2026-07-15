package com.pulse.common.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PulseMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        Metrics.addRegistry(meterRegistry);
    }

    @AfterEach
    void tearDown() {
        Metrics.removeRegistry(meterRegistry);
        Metrics.globalRegistry.clear();
        meterRegistry.close();
    }

    @Test
    void increment_shouldRecordCounterWithTags() {
        PulseMetrics.increment("pulse.score.task.consumed", "type", "live");

        assertThat(meterRegistry.get("pulse.score.task.consumed")
                .tag("type", "live")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void record_shouldRunActionAndRecordTimer() {
        AtomicBoolean executed = new AtomicBoolean(false);

        PulseMetrics.record("pulse.score.task.processing", () -> executed.set(true), "type", "terminal");

        assertThat(executed).isTrue();
        assertThat(meterRegistry.get("pulse.score.task.processing")
                .tag("type", "terminal")
                .timer()
                .count()).isEqualTo(1L);
    }
}
