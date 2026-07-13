package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.config.ScoringProperties;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlertSimulatorTest {
    @Test void 진입_해제_재무장과_쿨다운을_적용한다() {
        ScoringProperties properties = new ScoringConstantsLoader().loadBaseline("6");
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        List<Cycle> cycles = List.of(
                cycle(start, 86), cycle(start.plusSeconds(60), 90), cycle(start.plusSeconds(120), 60),
                cycle(start.plusSeconds(16 * 60), 86));
        assertThat(new AlertSimulator().simulate(cycles, properties)).isEqualTo(2);
    }
    private static Cycle cycle(Instant at, double score) { return new Cycle(at, at.getEpochSecond(), score, score, 0, "OPERATIONAL"); }
}
