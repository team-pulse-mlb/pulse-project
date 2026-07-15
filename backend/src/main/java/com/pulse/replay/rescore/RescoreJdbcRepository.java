package com.pulse.replay.rescore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("rescore")
@RequiredArgsConstructor
class RescoreJdbcRepository {

    private static final String RESCORE_PLAY_SOURCE_CONDITION =
            "source IN ('S3_LIVE_ARCHIVE', 'S3_BACKFILL')";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    List<Long> gameIdsWithPlays() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT game_id FROM plays WHERE " + RESCORE_PLAY_SOURCE_CONDITION + " ORDER BY game_id",
                Long.class);
    }

    List<RescorePlayRow> playsForGame(Long gameId) {
        String sql = """
                SELECT game_id, play_order, type, inning, inning_type, home_score, away_score,
                       scoring_play, score_value, outs, balls, strikes, observed_at, backfilled, source
                FROM plays
                WHERE game_id = ?
                  AND %s
                ORDER BY observed_at, play_order
                """.formatted(RESCORE_PLAY_SOURCE_CONDITION);
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RescorePlayRow(
                rs.getLong("game_id"),
                rs.getLong("play_order"),
                rs.getString("type"),
                nullableInt(rs, "inning"),
                rs.getString("inning_type"),
                nullableInt(rs, "home_score"),
                nullableInt(rs, "away_score"),
                nullableBoolean(rs, "scoring_play"),
                nullableInt(rs, "score_value"),
                nullableInt(rs, "outs"),
                nullableInt(rs, "balls"),
                nullableInt(rs, "strikes"),
                instant(rs.getObject("observed_at", OffsetDateTime.class)),
                nullableBoolean(rs, "backfilled"),
                rs.getString("source")), gameId);
    }

    int insertWatchScore(RescoreWatchScoreRow row) {
        String sql = """
                INSERT INTO watch_scores (
                    game_id, computed_at, play_order, inning, inning_type, base_score,
                    importance_multiplier, pregame_bonus, watch_score, scoring_version, signal_contributions,
                    tags, backfilled, source
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?::jsonb, ?, ?, ?)
                ON CONFLICT (game_id, computed_at) DO NOTHING
                """;
        return jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, row.gameId());
            setInstant(ps, 2, row.computedAt());
            ps.setObject(3, row.playOrder(), Types.BIGINT);
            ps.setObject(4, row.inning(), Types.SMALLINT);
            ps.setString(5, row.inningType());
            ps.setObject(6, row.baseScore(), Types.SMALLINT);
            ps.setObject(7, row.watchScore(), Types.SMALLINT);
            ps.setInt(8, row.scoringVersion());
            ps.setString(9, json(row));
            setTextArray(ps, 10, row.tags());
            ps.setBoolean(11, row.backfilled());
            ps.setString(12, row.source());
            return ps;
        });
    }

    private String json(RescoreWatchScoreRow row) {
        try {
            return objectMapper.writeValueAsString(row.signalContributions());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("signal_contributions JSON 변환 실패", e);
        }
    }

    private static void setInstant(PreparedStatement ps, int index, Instant instant) throws java.sql.SQLException {
        if (instant == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        ps.setObject(index, instant.atOffset(ZoneOffset.UTC));
    }

    private static void setTextArray(PreparedStatement ps, int index, List<String> values)
            throws java.sql.SQLException {
        String[] arrayValues = values == null ? new String[0] : values.toArray(String[]::new);
        Array sqlArray = ps.getConnection().createArrayOf("text", arrayValues);
        ps.setArray(index, sqlArray);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static Integer nullableInt(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean nullableBoolean(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
