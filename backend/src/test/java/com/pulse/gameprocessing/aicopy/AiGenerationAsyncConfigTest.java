package com.pulse.gameprocessing.aicopy;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiGenerationAsyncConfigTest {

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
    void rejectedExecution_shouldDropTaskAndIncrementMetric() {
        AtomicBoolean executed = new AtomicBoolean(false);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        try {
            new AiGenerationAsyncConfig.AiGenerationRejectedExecutionHandler()
                    .rejectedExecution(() -> executed.set(true), executor);

            assertThat(executed).isFalse();
            assertThat(meterRegistry.get("pulse.ai.generation.rejected")
                    .counter()
                    .count()).isEqualTo(1.0);
        } finally {
            executor.shutdownNow();
        }
    }
}
