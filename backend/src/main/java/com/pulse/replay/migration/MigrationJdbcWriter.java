package com.pulse.replay.migration;

import com.pulse.replay.migration.MigrationRows.DbPlayRow;
import com.pulse.replay.migration.MigrationRows.GameRow;
import com.pulse.replay.migration.MigrationRows.LineupRow;
import com.pulse.replay.migration.MigrationRows.OddsSnapshotRow;
import com.pulse.replay.migration.MigrationRows.PlayRow;
import com.pulse.replay.migration.MigrationRows.PlayerRow;
import com.pulse.replay.migration.MigrationRows.RunnerUpdate;
import com.pulse.replay.migration.MigrationRows.StandingRow;
import com.pulse.replay.migration.MigrationRows.TeamRow;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("migration")
class MigrationJdbcWriter {

    private final JdbcTemplate jdbcTemplate;

    MigrationJdbcWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    UpsertResult insertTeam(TeamRow row) {
        String sql = """
                INSERT INTO teams (
                    team_id, abbreviation, display_name, short_display_name, name,
                    location, slug, league, division
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (team_id) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, row.teamId(), row.abbreviation(), row.displayName(), row.shortDisplayName(),
                row.name(), row.location(), row.slug(), row.league(), row.division());
    }

    UpsertResult upsertGame(GameRow row) {
        String sql = """
                INSERT INTO games (
                    game_id, season, postseason, start_time, status, period,
                    home_team_id, away_team_id, home_runs, away_runs, home_hits, away_hits,
                    home_errors, away_errors, home_inning_scores, away_inning_scores,
                    venue, attendance, observed_at, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, 'S3_LIVE_ARCHIVE')
                ON CONFLICT (game_id) DO UPDATE SET
                    season = excluded.season,
                    postseason = excluded.postseason,
                    start_time = excluded.start_time,
                    status = excluded.status,
                    period = excluded.period,
                    home_team_id = excluded.home_team_id,
                    away_team_id = excluded.away_team_id,
                    home_runs = excluded.home_runs,
                    away_runs = excluded.away_runs,
                    home_hits = excluded.home_hits,
                    away_hits = excluded.away_hits,
                    home_errors = excluded.home_errors,
                    away_errors = excluded.away_errors,
                    home_inning_scores = excluded.home_inning_scores,
                    away_inning_scores = excluded.away_inning_scores,
                    venue = excluded.venue,
                    attendance = excluded.attendance,
                    observed_at = excluded.observed_at,
                    source = excluded.source
                WHERE games.source <> 'OPERATIONAL'
                  AND (games.observed_at IS NULL OR excluded.observed_at >= games.observed_at)
                RETURNING (xmax = 0) AS inserted
                """;
        return upsert(sql, row.gameId(), row.season(), row.postseason(), row.startTime(), row.status(), row.period(),
                row.homeTeamId(), row.awayTeamId(), row.homeRuns(), row.awayRuns(), row.homeHits(), row.awayHits(),
                row.homeErrors(), row.awayErrors(), row.homeInningScores(), row.awayInningScores(),
                row.venue(), row.attendance(), row.observedAt());
    }

    UpsertResult insertPlayer(PlayerRow row) {
        String sql = """
                INSERT INTO players (
                    player_id, full_name, first_name, last_name, position, team_id, jersey,
                    bats_throws, dob, debut_year, active
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, row.playerId(), row.fullName(), row.firstName(), row.lastName(), row.position(),
                row.teamId(), row.jersey(), row.batsThrows(), row.dob(), row.debutYear(), row.active());
    }

    UpsertResult insertMinimalPlayer(Long playerId) {
        String sql = """
                INSERT INTO players (player_id)
                VALUES (?)
                ON CONFLICT (player_id) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, playerId);
    }

    UpsertResult insertPlay(PlayRow row) {
        String sql = """
                INSERT INTO plays (
                    game_id, play_order, type, inning, inning_type, text,
                    home_score, away_score, scoring_play, score_value, outs, balls, strikes,
                    batter_id, pitcher_id, pitch_type, pitch_velocity, hit_coordinate_x,
                    hit_coordinate_y, trajectory, observed_at, backfilled, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (game_id, play_order) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, row.gameId(), row.playOrder(), row.type(), row.inning(), row.inningType(), row.text(),
                row.homeScore(), row.awayScore(), row.scoringPlay(), row.scoreValue(), row.outs(), row.balls(),
                row.strikes(), row.batterId(), row.pitcherId(), row.pitchType(), row.pitchVelocity(),
                row.hitCoordinateX(), row.hitCoordinateY(), row.trajectory(), row.observedAt(), row.backfilled(),
                row.source());
    }

    UpsertResult insertOddsSnapshot(OddsSnapshotRow row) {
        String sql = """
                INSERT INTO odds_snapshots (
                    game_id, vendor, snapshot_type, moneyline_home_odds, moneyline_away_odds,
                    spread_home_value, spread_away_value, spread_home_odds, spread_away_odds,
                    total_value, total_over_odds, total_under_odds, vendor_updated_at, observed_at, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'S3_LIVE_ARCHIVE')
                ON CONFLICT (game_id, vendor, snapshot_type) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, row.gameId(), row.vendor(), row.snapshotType(), row.moneylineHomeOdds(),
                row.moneylineAwayOdds(), row.spreadHomeValue(), row.spreadAwayValue(), row.spreadHomeOdds(),
                row.spreadAwayOdds(), row.totalValue(), row.totalOverOdds(), row.totalUnderOdds(),
                row.vendorUpdatedAt(), row.observedAt());
    }

    UpsertResult insertStanding(StandingRow row) {
        String sql = """
                INSERT INTO standings (
                    season, snapshot_date, team_id, league_name, division_name, wins, losses,
                    win_percent, games_behind, playoff_percent, wildcard_percent, streak,
                    last_ten_games, observed_at, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'S3_LIVE_ARCHIVE')
                ON CONFLICT (season, snapshot_date, team_id) DO NOTHING
                RETURNING true
                """;
        return insertOnly(sql, row.season(), row.snapshotDate(), row.teamId(), row.leagueName(), row.divisionName(),
                row.wins(), row.losses(), row.winPercent(), row.gamesBehind(), row.playoffPercent(),
                row.wildcardPercent(), row.streak(), row.lastTenGames(), row.observedAt());
    }

    UpsertResult upsertLineup(LineupRow row) {
        String sql = """
                INSERT INTO lineups (
                    lineup_item_id, game_id, player_id, team_id, batting_order,
                    position, is_probable_pitcher, observed_at, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'S3_LIVE_ARCHIVE')
                ON CONFLICT (game_id, player_id) DO UPDATE SET
                    batting_order = excluded.batting_order,
                    position = excluded.position,
                    is_probable_pitcher = excluded.is_probable_pitcher,
                    observed_at = excluded.observed_at
                WHERE lineups.source <> 'OPERATIONAL'
                  AND (lineups.observed_at IS NULL OR excluded.observed_at >= lineups.observed_at)
                RETURNING (xmax = 0) AS inserted
                """;
        return upsert(sql, row.lineupItemId(), row.gameId(), row.playerId(), row.teamId(), row.battingOrder(),
                row.position(), row.probablePitcher(), row.observedAt());
    }

    boolean gameExists(Long gameId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM games WHERE game_id = ?)",
                Boolean.class,
                gameId);
        return Boolean.TRUE.equals(exists);
    }

    Instant gameStartTime(Long gameId) {
        return jdbcTemplate.query(
                "SELECT start_time FROM games WHERE game_id = ?",
                ps -> ps.setLong(1, gameId),
                rs -> rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toInstant() : null);
    }

    List<DbPlayRow> playsForGame(Long gameId) {
        return jdbcTemplate.query(
                "SELECT game_id, play_order, inning, inning_type, batter_id FROM plays WHERE game_id = ? ORDER BY play_order",
                (rs, rowNum) -> new DbPlayRow(
                        rs.getLong("game_id"),
                        rs.getLong("play_order"),
                        nullableInt(rs, "inning"),
                        rs.getString("inning_type"),
                        nullableLong(rs, "batter_id")),
                gameId);
    }

    void updateRunners(List<RunnerUpdate> updates) {
        String sql = """
                UPDATE plays
                SET runner_on_first = ?, runner_on_second = ?, runner_on_third = ?
                WHERE game_id = ? AND play_order = ?
                """;
        jdbcTemplate.batchUpdate(sql, updates, 500, (PreparedStatement ps, RunnerUpdate row) -> {
            ps.setObject(1, row.runnerOnFirst(), Types.BOOLEAN);
            ps.setObject(2, row.runnerOnSecond(), Types.BOOLEAN);
            ps.setObject(3, row.runnerOnThird(), Types.BOOLEAN);
            ps.setLong(4, row.gameId());
            ps.setLong(5, row.playOrder());
        });
    }

    private UpsertResult insertOnly(String sql, Object... args) {
        Boolean inserted = jdbcTemplate.query(sql, ps -> setArgs(ps, args), rs -> rs.next() ? rs.getBoolean(1) : null);
        return Boolean.TRUE.equals(inserted) ? UpsertResult.INSERTED : UpsertResult.SKIPPED;
    }

    private UpsertResult upsert(String sql, Object... args) {
        Boolean inserted = jdbcTemplate.query(sql, ps -> setArgs(ps, args), rs -> rs.next() ? rs.getBoolean(1) : null);
        if (inserted == null) {
            return UpsertResult.SKIPPED;
        }
        return inserted ? UpsertResult.INSERTED : UpsertResult.UPDATED;
    }

    private static void setArgs(PreparedStatement ps, Object... args) throws java.sql.SQLException {
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            // PgJDBC setObject는 Instant를 직접 지원하지 않으므로 OffsetDateTime으로 변환한다.
            if (arg instanceof Instant instant) {
                arg = instant.atOffset(ZoneOffset.UTC);
            }
            ps.setObject(i + 1, arg);
        }
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    enum UpsertResult {
        INSERTED,
        UPDATED,
        SKIPPED
    }
}

