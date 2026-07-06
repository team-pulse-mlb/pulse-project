package com.pulse.replay.migration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

final class MigrationRows {

    private MigrationRows() {
    }

    record TeamRow(Long teamId, String abbreviation, String displayName, String shortDisplayName,
                   String name, String location, String slug, String league, String division) {
    }

    record GameRow(Long gameId, Integer season, Boolean postseason, Instant startTime, String status,
                   Integer period, Long homeTeamId, Long awayTeamId, Integer homeRuns, Integer awayRuns,
                   Integer homeHits, Integer awayHits, Integer homeErrors, Integer awayErrors,
                   String homeInningScores, String awayInningScores, String venue, Integer attendance,
                   Instant observedAt) {
    }

    record PlayerRow(Long playerId, String fullName, String firstName, String lastName, String position,
                     Long teamId, String jersey, String batsThrows, LocalDate dob, Integer debutYear,
                     Boolean active) {
    }

    record PlayRow(Long gameId, Long playOrder, String type, Integer inning, String inningType, String text,
                   Integer homeScore, Integer awayScore, Boolean scoringPlay, Integer scoreValue,
                   Integer outs, Integer balls, Integer strikes, Long batterId, Long pitcherId,
                   String pitchType, Integer pitchVelocity, Integer hitCoordinateX, Integer hitCoordinateY,
                   String trajectory, Instant observedAt, boolean backfilled, String source) {
    }

    record PlateAppearanceRow(Long gameId, Long paNumber, Integer inning, String halfInning, Long batterId,
                              Long pitcherId, Boolean runnerOnFirst, Boolean runnerOnSecond, Boolean runnerOnThird,
                              Instant observedAt) {
    }

    record DbPlayRow(Long gameId, Long playOrder, Integer inning, String inningType, Long batterId) {
    }

    record RunnerUpdate(Long gameId, Long playOrder, Boolean runnerOnFirst, Boolean runnerOnSecond,
                        Boolean runnerOnThird) {
    }

    record OddsObservation(Long gameId, String vendor, Integer moneylineHomeOdds, Integer moneylineAwayOdds,
                           BigDecimal spreadHomeValue, BigDecimal spreadAwayValue, Integer spreadHomeOdds,
                           Integer spreadAwayOdds, BigDecimal totalValue, Integer totalOverOdds,
                           Integer totalUnderOdds, Instant vendorUpdatedAt, Instant observedAt) {
        Instant pregameTime() {
            return vendorUpdatedAt == null ? observedAt : vendorUpdatedAt;
        }
    }

    record OddsSnapshotRow(Long gameId, String vendor, String snapshotType, Integer moneylineHomeOdds,
                           Integer moneylineAwayOdds, BigDecimal spreadHomeValue, BigDecimal spreadAwayValue,
                           Integer spreadHomeOdds, Integer spreadAwayOdds, BigDecimal totalValue,
                           Integer totalOverOdds, Integer totalUnderOdds, Instant vendorUpdatedAt,
                           Instant observedAt) {
    }

    record StandingRow(Integer season, LocalDate snapshotDate, Long teamId, String leagueName,
                       String divisionName, Integer wins, Integer losses, BigDecimal winPercent,
                       BigDecimal gamesBehind, BigDecimal playoffPercent, BigDecimal wildcardPercent,
                       Integer streak, Integer lastTenGames, Instant observedAt) {
    }

    record LineupRow(Long lineupItemId, Long gameId, Long playerId, Long teamId, Integer battingOrder,
                     String position, Boolean probablePitcher, Instant observedAt) {
    }
}
