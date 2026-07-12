package com.pulse.replay.backtest;

import static com.pulse.replay.backtest.BacktestModels.GameData;
import static com.pulse.replay.backtest.BacktestModels.GameRow;
import static com.pulse.replay.backtest.BacktestModels.OddsRow;
import static com.pulse.replay.backtest.BacktestModels.PlayRow;
import static com.pulse.replay.backtest.BacktestModels.StandingRow;

import com.pulse.domain.OddsSnapshot;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("backtest")
@RequiredArgsConstructor
class BacktestJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    List<GameData> load(BacktestProperties properties) {
        List<Object> arguments = new ArrayList<>(List.of(
                properties.from().atStartOfDay().atOffset(ZoneOffset.UTC),
                properties.to().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)));
        StringBuilder sql = new StringBuilder("""
                SELECT g.game_id, g.start_time, g.status, g.postseason, g.home_team_id, g.away_team_id,
                       g.home_runs, g.away_runs, g.period, g.pregame_score, g.pregame_inputs,
                       g.home_team_abbr, g.away_team_abbr
                FROM games g
                WHERE g.start_time >= ? AND g.start_time < ?
                """);
        appendIn(sql, arguments, "g.game_id", properties.gameIds());
        sql.append(" ORDER BY g.start_time, g.game_id");
        List<GameRow> games = jdbcTemplate.query(sql.toString(), this::gameRow, arguments.toArray());
        return games.stream().map(game -> new GameData(
                game,
                plays(game.gameId(), properties.sources()),
                standing(game.homeTeamId(), date(game)),
                standing(game.awayTeamId(), date(game)),
                preferredOdds(game.gameId()))).toList();
    }

    private List<PlayRow> plays(long gameId, List<String> sources) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(gameId);
        StringBuilder sql = new StringBuilder("""
                SELECT game_id, play_order, type, inning, inning_type, home_score, away_score, scoring_play,
                       score_value, outs, balls, strikes, observed_at, backfilled, source,
                       runner_on_first, runner_on_second, runner_on_third
                FROM plays WHERE game_id = ?
                """);
        appendIn(sql, arguments, "source", sources);
        sql.append(" ORDER BY observed_at NULLS FIRST, play_order");
        return jdbcTemplate.query(sql.toString(), (rs, index) -> new PlayRow(
                rs.getLong("game_id"), rs.getLong("play_order"), rs.getString("type"), integer(rs, "inning"),
                rs.getString("inning_type"), integer(rs, "home_score"), integer(rs, "away_score"),
                bool(rs, "scoring_play"), integer(rs, "score_value"), integer(rs, "outs"), integer(rs, "balls"),
                integer(rs, "strikes"), instant(rs), bool(rs, "backfilled"), rs.getString("source"),
                bool(rs, "runner_on_first"), bool(rs, "runner_on_second"), bool(rs, "runner_on_third")),
                arguments.toArray());
    }

    private StandingRow standing(Long teamId, LocalDate date) {
        if (teamId == null) {
            return null;
        }
        return jdbcTemplate.query("""
                SELECT DISTINCT ON (team_id) playoff_percent, win_percent FROM standings
                WHERE team_id = ? AND snapshot_date <= ? ORDER BY team_id, snapshot_date DESC
                """, rs -> rs.next() ? new StandingRow(rs.getBigDecimal(1), rs.getBigDecimal(2)) : null, teamId, date);
    }

    private List<OddsRow> preferredOdds(long gameId) {
        List<OddsRow> finalRows = odds(gameId, OddsSnapshot.SNAPSHOT_PREGAME_FINAL);
        return finalRows.isEmpty() ? odds(gameId, OddsSnapshot.SNAPSHOT_FIRST_SEEN) : finalRows;
    }

    private List<OddsRow> odds(long gameId, String type) {
        return jdbcTemplate.query("""
                SELECT moneyline_home_odds, moneyline_away_odds FROM odds_snapshots
                WHERE game_id = ? AND snapshot_type = ? ORDER BY vendor
                """, (rs, index) -> new OddsRow(integer(rs, 1), integer(rs, 2)), gameId, type);
    }

    private GameRow gameRow(ResultSet rs, int index) throws SQLException {
        return new GameRow(rs.getLong("game_id"), instant(rs, "start_time"), rs.getString("status"),
                Boolean.TRUE.equals(bool(rs, "postseason")), rs.getObject("home_team_id", Long.class),
                rs.getObject("away_team_id", Long.class), integer(rs, "home_runs"), integer(rs, "away_runs"),
                integer(rs, "period"), integer(rs, "pregame_score"), rs.getString("pregame_inputs"),
                rs.getString("home_team_abbr"), rs.getString("away_team_abbr"));
    }

    private static LocalDate date(GameRow game) {
        return game.startTime().atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private static void appendIn(StringBuilder sql, List<Object> args, String column, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" AND ").append(column).append(" IN (").append("?,".repeat(values.size()));
        sql.setLength(sql.length() - 1);
        sql.append(')');
        args.addAll(values);
    }

    private static Integer integer(ResultSet rs, String name) throws SQLException {
        int value = rs.getInt(name);
        return rs.wasNull() ? null : value;
    }

    private static Integer integer(ResultSet rs, int index) throws SQLException {
        int value = rs.getInt(index);
        return rs.wasNull() ? null : value;
    }

    private static Boolean bool(ResultSet rs, String name) throws SQLException {
        boolean value = rs.getBoolean(name);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs) throws SQLException {
        return instant(rs, "observed_at");
    }

    private static Instant instant(ResultSet rs, String name) throws SQLException {
        OffsetDateTime value = rs.getObject(name, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
