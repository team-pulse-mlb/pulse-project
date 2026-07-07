package com.pulse.replay.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.replay.migration.MigrationRows.OddsObservation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OddsSnapshotSelectorTest {

    private final OddsSnapshotSelector selector = new OddsSnapshotSelector();

    @Test
    @DisplayName("updated_at이 없으면 observed_at으로 pregame 시각을 판정한다")
    void fallsBackToObservedAt() {
        Instant startTime = Instant.parse("2026-07-01T20:00:00Z");
        OddsObservation observation = observation(null, Instant.parse("2026-07-01T19:00:00Z"));

        var rows = selector.select(List.of(observation), startTime);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(MigrationRows.OddsSnapshotRow::snapshotType)
                .containsExactly("FIRST_SEEN", "PREGAME_FINAL");
    }

    @Test
    @DisplayName("경기 시작 후 관측은 제외하고 시작 전 첫/마지막 관측만 선택한다")
    void skipsPostStartObservations() {
        Instant startTime = Instant.parse("2026-07-01T20:00:00Z");
        OddsObservation first = observation(Instant.parse("2026-07-01T18:00:00Z"), Instant.parse("2026-07-01T18:01:00Z"));
        OddsObservation last = observation(Instant.parse("2026-07-01T19:30:00Z"), Instant.parse("2026-07-01T19:31:00Z"));
        OddsObservation after = observation(Instant.parse("2026-07-01T20:01:00Z"), Instant.parse("2026-07-01T20:01:00Z"));

        var rows = selector.select(List.of(after, last, first), startTime);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).observedAt()).isEqualTo(first.observedAt());
        assertThat(rows.get(1).observedAt()).isEqualTo(last.observedAt());
    }

    @Test
    @DisplayName("pregame 관측이 하나뿐이면 같은 관측으로 두 snapshot을 만든다")
    void emitsTwoRowsForSingleObservation() {
        OddsObservation observation = observation(
                Instant.parse("2026-07-01T19:59:00Z"),
                Instant.parse("2026-07-01T19:59:01Z"));

        var rows = selector.select(List.of(observation), Instant.parse("2026-07-01T20:00:00Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).observedAt()).isEqualTo(rows.get(1).observedAt());
    }

    private static OddsObservation observation(Instant updatedAt, Instant observedAt) {
        return new OddsObservation(1L, "book", 100, -110,
                new BigDecimal("-1.5"), new BigDecimal("1.5"), -105, -115,
                new BigDecimal("8.5"), -110, -110, updatedAt, observedAt);
    }
}
