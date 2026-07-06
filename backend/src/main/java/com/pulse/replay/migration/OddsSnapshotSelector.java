package com.pulse.replay.migration;

import com.pulse.replay.migration.MigrationRows.OddsObservation;
import com.pulse.replay.migration.MigrationRows.OddsSnapshotRow;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

class OddsSnapshotSelector {

    List<OddsSnapshotRow> select(List<OddsObservation> observations, Instant startTime) {
        List<OddsObservation> pregame = observations.stream()
                .filter(observation -> observation.pregameTime() != null)
                .filter(observation -> !observation.pregameTime().isAfter(startTime))
                .sorted(Comparator.comparing(OddsObservation::pregameTime)
                        .thenComparing(OddsObservation::observedAt))
                .toList();
        if (pregame.isEmpty()) {
            return List.of();
        }
        OddsObservation first = pregame.getFirst();
        OddsObservation last = pregame.getLast();
        return List.of(toSnapshot(first, "FIRST_SEEN"), toSnapshot(last, "PREGAME_FINAL"));
    }

    private OddsSnapshotRow toSnapshot(OddsObservation observation, String snapshotType) {
        return new OddsSnapshotRow(
                observation.gameId(),
                observation.vendor(),
                snapshotType,
                observation.moneylineHomeOdds(),
                observation.moneylineAwayOdds(),
                observation.spreadHomeValue(),
                observation.spreadAwayValue(),
                observation.spreadHomeOdds(),
                observation.spreadAwayOdds(),
                observation.totalValue(),
                observation.totalOverOdds(),
                observation.totalUnderOdds(),
                observation.vendorUpdatedAt(),
                observation.observedAt()
        );
    }
}

