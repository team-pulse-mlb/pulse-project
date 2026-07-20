package com.pulse.replay.backtest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class BacktestModels {
    private BacktestModels() {}

    public record GameRow(long gameId, Instant startTime, String status, boolean postseason, Long homeTeamId,
                   Long awayTeamId, Integer homeRuns, Integer awayRuns, Integer period, Integer pregameScore,
                   String pregameInputs, String homeTeam, String awayTeam) {}

    public record PlayRow(long gameId, long playOrder, String type, Integer inning, String inningType,
                   Integer homeScore, Integer awayScore, Boolean scoringPlay, Integer scoreValue,
                   Integer outs, Integer balls, Integer strikes, Instant observedAt, Boolean backfilled,
                   String source, Boolean runnerOnFirst, Boolean runnerOnSecond, Boolean runnerOnThird) {}

    public record StandingRow(BigDecimal playoffPercent, BigDecimal winPercent) {}
    public record OddsRow(Integer homeOdds, Integer awayOdds) {}
    public record GameData(GameRow game, List<PlayRow> plays, StandingRow homeStanding,
                    StandingRow awayStanding, List<OddsRow> odds) {}

    public record Cycle(Instant computedAt, long playOrder, double baseScore, double stateScore, double watchScore,
                 int latestPlayIndex, String source, Map<String, Double> signals) {}
    public record ReplayResult(GameData data, List<Cycle> cycles, int alertCount) {
        double peak() { return cycles.stream().mapToDouble(Cycle::watchScore).max().orElse(0); }
        LocalDate date() { return data.game().startTime().atOffset(java.time.ZoneOffset.UTC).toLocalDate(); }
    }
}
