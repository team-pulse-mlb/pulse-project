package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.Cycle;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.config.ScoringProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Test
    void 전역_윈도_한도와_만료를_모든_경기에_적용한다() {
        ScoringProperties properties = new ScoringConstantsLoader().loadBaseline("6");
        Instant start = Instant.parse("2026-06-01T00:00:00Z");
        Map<Long, List<Cycle>> cyclesByGame = new LinkedHashMap<>();
        cyclesByGame.put(1L, List.of(cycle(start, 86)));
        cyclesByGame.put(2L, List.of(cycle(start.plusSeconds(60), 86)));
        cyclesByGame.put(3L, List.of(cycle(start.plusSeconds(120), 86)));
        cyclesByGame.put(4L, List.of(
                cycle(start.plusSeconds(180), 86),
                cycle(start.plusSeconds(15 * 60), 86)));

        assertThat(new AlertSimulator().simulate(cyclesByGame, properties))
                .containsExactly(
                        Map.entry(1L, 1),
                        Map.entry(2L, 1),
                        Map.entry(3L, 1),
                        Map.entry(4L, 1));
    }

    @Test
    void 시간_정보가_없는_백필은_전역_윈도에서_제외한다() {
        ScoringProperties properties = new ScoringConstantsLoader().loadBaseline("6");
        Map<Long, List<Cycle>> cyclesByGame = new LinkedHashMap<>();
        cyclesByGame.put(1L, List.of(backfillCycle(1, 86)));
        cyclesByGame.put(2L, List.of(backfillCycle(1, 86)));
        cyclesByGame.put(3L, List.of(backfillCycle(1, 86)));
        cyclesByGame.put(4L, List.of(backfillCycle(1, 86)));

        assertThat(new AlertSimulator().simulate(cyclesByGame, properties).values())
                .containsExactly(1, 1, 1, 1);
    }

    private static Cycle cycle(Instant at, double score) {
        return new Cycle(at, at.getEpochSecond(), score, score, score, 0, "OPERATIONAL");
    }

    private static Cycle backfillCycle(long playOrder, double score) {
        return new Cycle(null, playOrder, score, score, score, 0, "S3_BACKFILL");
    }
}
