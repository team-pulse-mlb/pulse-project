package com.pulse.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 역할별 운영 흐름에서 공통으로 사용하는 PULSE 도메인 지표 이름과 기록 창구. */
public final class PulseMetrics {

    private static final ConcurrentMap<GaugeKey, AtomicLong> GAUGES = new ConcurrentHashMap<>();

    private PulseMetrics() {
    }

    public static void increment(String name, String... tags) {
        Counter.builder(name).tags(tags).register(Metrics.globalRegistry).increment();
    }

    public static void gauge(String name, long value, String... tags) {
        GaugeKey key = new GaugeKey(name, List.copyOf(Arrays.asList(tags)));
        AtomicLong gauge = GAUGES.computeIfAbsent(key, ignored -> new AtomicLong());
        Gauge.builder(name, gauge, AtomicLong::get)
                .tags(Tags.of(tags))
                .strongReference(true)
                .register(Metrics.globalRegistry);
        gauge.set(value);
    }

    public static void record(String name, Runnable action, String... tags) {
        Timer.builder(name).tags(tags).register(Metrics.globalRegistry).record(action);
    }

    public static <T> T record(String name, Supplier<T> action, String... tags) {
        return Timer.builder(name).tags(tags).register(Metrics.globalRegistry).record(action);
    }

    private record GaugeKey(String name, List<String> tags) {
    }
}
