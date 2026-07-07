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

    void lockGameForReplaySegments(Long gameId) {
        jdbcTemplate.queryForObject(
                "SELECT game_id FROM games WHERE game_id = ? FOR UPDATE",
                Long.class,
                gameId);
    }

    boolean replaySegmentsExist(Long gameId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM replay_segments WHERE game_id = ?)",
                Boolean.class,
                gameId);
        return Boolean.TRUE.equals(exists);
    }

    int insertWatchScore(RescoreWatchScoreRow row) {
        String sql = """
                INSERT INTO watch_scores (
                    game_id, computed_at, play_order, inning, inning_type, base_score,
                    importance_multiplier, pregame_bonus, watch_score, signal_contributions,
                    tags, backfilled, source
                )
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?::jsonb, ?, ?, ?)
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
            ps.setString(8, json(row));
            setTextArray(ps, 9, row.tags());
            ps.setBoolean(10, row.backfilled());
            ps.setString(11, row.source());
            return ps;
        });
    }

    int insertReplaySegments(List<ReplaySegmentDraft> segments) {
        String sql = """
                INSERT INTO replay_segments (
                    game_id, start_play_order, end_play_order, start_inning, end_inning,
                    start_inning_type, end_inning_type, peak_score, tags, ai_summary,
                    status, opened_at, closed_at, source
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                """;
        int inserted = 0;
        for (ReplaySegmentDraft segment : segments) {
            inserted += jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setLong(1, segment.gameId());
                ps.setObject(2, segment.startPlayOrder(), Types.BIGINT);
                ps.setObject(3, segment.endPlayOrder(), Types.BIGINT);
                ps.setObject(4, segment.startInning(), Types.SMALLINT);
                ps.setObject(5, segment.endInning(), Types.SMALLINT);
                ps.setString(6, segment.startInningType());
                ps.setString(7, segment.endInningType());
                ps.setObject(8, segment.peakScore(), Types.SMALLINT);
                setTextArray(ps, 9, segment.tags());
                ps.setString(10, segment.status());
                setInstant(ps, 11, segment.openedAt());
                setInstant(ps, 12, segment.closedAt());
                ps.setString(13, segment.source());
                return ps;
            });
        }
        return inserted;
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
