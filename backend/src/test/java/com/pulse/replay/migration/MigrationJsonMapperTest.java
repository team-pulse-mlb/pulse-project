package com.pulse.replay.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationJsonMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MigrationJsonMapper mapper = new MigrationJsonMapper(objectMapper);

    @Test
    @DisplayName("게임 JSON 결측 필드는 null로 매핑한다")
    void mapsMissingGameFieldsToNull() throws Exception {
        var response = objectMapper.readTree("""
                {"data":[{"id":1,"home_team":{"id":10},"away_team":{"id":20},"date":"2026-07-01T20:00:00Z"}]}
                """);
        var envelope = new MigrationEnvelope("raw/games/dt=2026-07-01/games.json.gz",
                Instant.parse("2026-07-01T19:00:00Z"), "/games", objectMapper.createObjectNode(), response, false);

        var row = mapper.game(envelope, mapper.dataNodes(response).getFirst());

        assertThat(row.gameId()).isEqualTo(1L);
        assertThat(row.status()).isNull();
        assertThat(row.homeRuns()).isNull();
        assertThat(row.homeInningScores()).isNull();
    }

    @Test
    @DisplayName("odds spread/total 문자열은 BigDecimal로 변환하고 실패하면 null로 둔다")
    void parsesOddsDecimalFieldsDefensively() throws Exception {
        var response = objectMapper.readTree("""
                {"data":[{"game_id":1,"vendor":"book","spread_home_value":"-1.5","spread_away_value":"bad","total_value":"8.5"}]}
                """);
        var envelope = new MigrationEnvelope("raw/odds/dt=2026-07-01/odds.json.gz",
                Instant.parse("2026-07-01T19:00:00Z"), "/odds", objectMapper.createObjectNode(), response, false);

        var row = mapper.odds(envelope, mapper.dataNodes(response).getFirst());

        assertThat(row.spreadHomeValue()).isEqualByComparingTo(new BigDecimal("-1.5"));
        assertThat(row.spreadAwayValue()).isNull();
        assertThat(row.totalValue()).isEqualByComparingTo(new BigDecimal("8.5"));
    }

    @Test
    @DisplayName("play hit coordinate는 평면 필드와 중첩 필드를 모두 처리한다")
    void mapsPlayCoordinatesDefensively() throws Exception {
        var response = objectMapper.readTree("""
                {"data":[{"order":7,"hit_coordinate":{"x":12,"y":34}}]}
                """);
        var params = objectMapper.readTree("{\"game_id\":99}");
        var envelope = new MigrationEnvelope("raw/plays/game_id=99/play.json.gz",
                Instant.parse("2026-07-01T19:00:00Z"), "/plays", params, response, false);

        var row = mapper.play(envelope, mapper.dataNodes(response).getFirst(), false, "S3_LIVE_ARCHIVE");

        assertThat(row.gameId()).isEqualTo(99L);
        assertThat(row.hitCoordinateX()).isEqualTo(12);
        assertThat(row.hitCoordinateY()).isEqualTo(34);
    }
}

