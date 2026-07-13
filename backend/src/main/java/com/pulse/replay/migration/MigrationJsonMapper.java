package com.pulse.replay.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.replay.migration.MigrationRows.GameRow;
import com.pulse.replay.migration.MigrationRows.LineupRow;
import com.pulse.replay.migration.MigrationRows.OddsObservation;
import com.pulse.replay.migration.MigrationRows.PlateAppearanceRow;
import com.pulse.replay.migration.MigrationRows.PlayRow;
import com.pulse.replay.migration.MigrationRows.PlayerRow;
import com.pulse.replay.migration.MigrationRows.StandingRow;
import com.pulse.replay.migration.MigrationRows.TeamRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class MigrationJsonMapper {

    private final ObjectMapper objectMapper;

    MigrationJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<JsonNode> dataNodes(JsonNode response) {
        JsonNode data = response == null ? null : response.path("data");
        if (data != null && data.isArray()) {
            return iterableToList(data);
        }
        if (response != null && response.isArray()) {
            return iterableToList(response);
        }
        return List.of();
    }

    List<TeamRow> teamsFromGames(MigrationEnvelope envelope) {
        List<TeamRow> rows = new ArrayList<>();
        for (JsonNode node : dataNodes(envelope.response())) {
            addTeam(rows, node.path("home_team"));
            addTeam(rows, node.path("away_team"));
        }
        return rows;
    }

    List<TeamRow> teamsFromNode(MigrationEnvelope envelope) {
        List<TeamRow> rows = new ArrayList<>();
        for (JsonNode node : dataNodes(envelope.response())) {
            addTeam(rows, node.path("team"));
        }
        return rows;
    }

    GameRow game(MigrationEnvelope envelope, JsonNode node) {
        JsonNode homeData = node.path("home_team_data");
        JsonNode awayData = node.path("away_team_data");
        Long gameId = longValue(node.path("id"));
        Long homeTeamId = longValue(node.path("home_team").path("id"));
        Long awayTeamId = longValue(node.path("away_team").path("id"));
        if (gameId == null || homeTeamId == null || awayTeamId == null) {
            return null;
        }
        return new GameRow(
                gameId,
                intValue(node.path("season")),
                boolValue(node.path("postseason")),
                instantValue(node.path("date")),
                textValue(node.path("status")),
                intValue(node.path("period")),
                homeTeamId,
                awayTeamId,
                intValue(homeData.path("runs")),
                intValue(awayData.path("runs")),
                intValue(homeData.path("hits")),
                intValue(awayData.path("hits")),
                intValue(homeData.path("errors")),
                intValue(awayData.path("errors")),
                jsonValue(homeData.path("inning_scores")),
                jsonValue(awayData.path("inning_scores")),
                textValue(node.path("venue")),
                intValue(node.path("attendance")),
                envelope.observedAt()
        );
    }

    PlayerRow player(JsonNode node) {
        JsonNode player = node.path("player");
        Long playerId = longValue(player.path("id"));
        if (playerId == null) {
            return null;
        }
        return new PlayerRow(
                playerId,
                firstText(player, "full_name", "display_name", "name"),
                textValue(player.path("first_name")),
                textValue(player.path("last_name")),
                firstText(player, "position", "primary_position"),
                longValue(node.path("team").path("id")),
                textValue(player.path("jersey")),
                firstText(player, "bats_throws", "bat_throw"),
                dateValue(player.path("dob")),
                intValue(player.path("debut_year")),
                boolValue(player.path("active"))
        );
    }

    PlayRow play(MigrationEnvelope envelope, JsonNode node, boolean backfilled, String source) {
        Long gameId = gameIdFrom(envelope.params(), envelope.key());
        Long playOrder = longValue(node.path("order"));
        if (gameId == null || playOrder == null) {
            return null;
        }
        JsonNode coordinate = node.path("hit_coordinate");
        return new PlayRow(
                gameId,
                playOrder,
                textValue(node.path("type")),
                intValue(node.path("inning")),
                textValue(node.path("inning_type")),
                textValue(node.path("text")),
                intValue(node.path("home_score")),
                intValue(node.path("away_score")),
                boolValue(node.path("scoring_play")),
                intValue(node.path("score_value")),
                intValue(node.path("outs")),
                intValue(node.path("balls")),
                intValue(node.path("strikes")),
                longValue(node.path("batter_id")),
                longValue(node.path("pitcher_id")),
                textValue(node.path("pitch_type")),
                intValue(node.path("pitch_velocity")),
                firstInt(node.path("hit_coordinate_x"), coordinate.path("x")),
                firstInt(node.path("hit_coordinate_y"), coordinate.path("y")),
                textValue(node.path("trajectory")),
                envelope.observedAt(),
                backfilled,
                source
        );
    }

    PlateAppearanceRow plateAppearance(MigrationEnvelope envelope, JsonNode node) {
        Long gameId = gameIdFrom(envelope.params(), envelope.key());
        Long paNumber = longValue(node.path("pa_number"));
        Long batterId = longValue(node.path("batter_id"));
        if (gameId == null || paNumber == null || batterId == null) {
            return null;
        }
        return new PlateAppearanceRow(
                gameId,
                paNumber,
                intValue(node.path("inning")),
                textValue(node.path("half_inning")),
                batterId,
                longValue(node.path("pitcher_id")),
                boolValue(node.path("runner_on_first")),
                boolValue(node.path("runner_on_second")),
                boolValue(node.path("runner_on_third")),
                envelope.observedAt()
        );
    }

    OddsObservation odds(MigrationEnvelope envelope, JsonNode node) {
        Long gameId = firstLong(node, "game_id", "game");
        String vendor = firstText(node, "vendor", "sportsbook", "book");
        if (gameId == null || vendor == null) {
            return null;
        }
        return new OddsObservation(
                gameId,
                vendor,
                firstInt(node, "moneyline_home_odds", "home_moneyline", "home_ml"),
                firstInt(node, "moneyline_away_odds", "away_moneyline", "away_ml"),
                decimalValue(firstNode(node, "spread_home_value", "home_spread")),
                decimalValue(firstNode(node, "spread_away_value", "away_spread")),
                firstInt(node, "spread_home_odds", "home_spread_odds"),
                firstInt(node, "spread_away_odds", "away_spread_odds"),
                decimalValue(firstNode(node, "total_value", "total")),
                firstInt(node, "total_over_odds", "over_odds"),
                firstInt(node, "total_under_odds", "under_odds"),
                instantValue(node.path("updated_at")),
                envelope.observedAt()
        );
    }

    StandingRow standing(MigrationEnvelope envelope, JsonNode node) {
        Long teamId = longValue(node.path("team").path("id"));
        LocalDate snapshotDate = dateFromKey(envelope.key());
        if (teamId == null || snapshotDate == null) {
            return null;
        }
        return new StandingRow(
                intValue(envelope.params().path("season")),
                snapshotDate,
                teamId,
                textValue(node.path("league_name")),
                textValue(node.path("division_name")),
                intValue(node.path("wins")),
                intValue(node.path("losses")),
                decimalValue(node.path("win_percent")),
                decimalValue(node.path("games_behind")),
                decimalValue(node.path("playoff_percent")),
                decimalValue(node.path("wildcard_percent")),
                intValue(node.path("streak")),
                textValue(node.path("last_ten_games")),
                envelope.observedAt()
        );
    }

    LineupRow lineup(MigrationEnvelope envelope, JsonNode node) {
        Long gameId = longValue(node.path("game_id"));
        Long playerId = longValue(node.path("player").path("id"));
        Long teamId = longValue(node.path("team").path("id"));
        Long lineupItemId = longValue(node.path("id"));
        if (gameId == null || playerId == null || teamId == null || lineupItemId == null) {
            return null;
        }
        return new LineupRow(
                lineupItemId,
                gameId,
                playerId,
                teamId,
                intValue(node.path("batting_order")),
                textValue(node.path("position")),
                boolValue(node.path("is_probable_pitcher")),
                envelope.observedAt()
        );
    }

    static Long gameIdFrom(JsonNode params, String key) {
        if (params != null) {
            JsonNode value = params.path("game_id");
            if (value.canConvertToLong()) {
                return value.asLong();
            }
        }
        String marker = "game_id=";
        int start = key.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int valueEnd = key.indexOf('/', valueStart);
        String raw = valueEnd < 0 ? key.substring(valueStart) : key.substring(valueStart, valueEnd);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static BigDecimal decimalValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void addTeam(List<TeamRow> rows, JsonNode team) {
        Long teamId = longValue(team.path("id"));
        if (teamId == null) {
            return;
        }
        rows.add(new TeamRow(
                teamId,
                textValue(team.path("abbreviation")),
                textValue(team.path("display_name")),
                textValue(team.path("short_display_name")),
                textValue(team.path("name")),
                textValue(team.path("location")),
                textValue(team.path("slug")),
                textValue(team.path("league")),
                textValue(team.path("division"))
        ));
    }

    private String jsonValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static List<JsonNode> iterableToList(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        array.forEach(nodes::add);
        return nodes;
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static Long longValue(JsonNode node) {
        return node != null && node.canConvertToLong() ? node.asLong() : null;
    }

    private static Integer intValue(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.asInt() : null;
    }

    private static Boolean boolValue(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull() ? node.asBoolean() : null;
    }

    private static Instant instantValue(JsonNode node) {
        String value = textValue(node);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate dateValue(JsonNode node) {
        String value = textValue(node);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate dateFromKey(String key) {
        String marker = "dt=";
        int start = key.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int end = key.indexOf('/', start + marker.length());
        String raw = end < 0 ? key.substring(start + marker.length()) : key.substring(start + marker.length(), end);
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Integer firstInt(JsonNode first, JsonNode second) {
        Integer value = intValue(first);
        return value == null ? intValue(second) : value;
    }

    private static Long firstLong(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null) {
            return null;
        }
        if (value.canConvertToLong()) {
            return value.asLong();
        }
        return longValue(value.path("id"));
    }

    private static Integer firstInt(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        return intValue(value);
    }

    private static String firstText(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        return textValue(value);
    }

    private static JsonNode firstNode(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
