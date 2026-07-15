package com.pulse.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.common.client.BdlDtos.BdlGame;
import org.junit.jupiter.api.Test;

class BdlDtosTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void bdlGame_shouldDeserializeTopLevelTeamNames() throws Exception {
        String response = """
                {
                  "id": 8712499,
                  "date": "2026-07-15T00:00:00.000Z",
                  "status": "STATUS_SCHEDULED",
                  "period": 1,
                  "home_team_name": "National All-Stars",
                  "away_team_name": "American All-Stars",
                  "home_team": { "id": -1, "display_name": "Unknown", "abbreviation": "UNK" },
                  "away_team": { "id": -1, "display_name": "Unknown", "abbreviation": "UNK" },
                  "venue": "Citizens Bank Park"
                }
                """;

        BdlGame game = objectMapper.readValue(response, BdlGame.class);

        assertThat(game.homeTeamName()).isEqualTo("National All-Stars");
        assertThat(game.awayTeamName()).isEqualTo("American All-Stars");
    }
}
