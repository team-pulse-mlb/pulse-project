package com.pulse.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;

/** 역할별 운영 흐름에서 공통으로 사용하는 PULSE 도메인 지표 이름과 기록 창구. */
public final class PulseMetrics {

    private PulseMetrics() {
    }

    public static void increment(String name, String... tags) {
        Counter.builder(name).tags(tags).register(Metrics.globalRegistry).increment();
    }

    public static void record(String name, Runnable action, String... tags) {
        Timer.builder(name).tags(tags).register(Metrics.globalRegistry).record(action);
    }

    public static <T> T record(String name, Supplier<T> action, String... tags) {
        return Timer.builder(name).tags(tags).register(Metrics.globalRegistry).record(action);
    }
}
