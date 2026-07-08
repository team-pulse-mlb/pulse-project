package com.pulse.common.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * balldontlie MLB API 응답 DTO 모음.
 */
public final class BdlDtos {

    private BdlDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ListResponse<T>(List<T> data, Meta meta) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Meta(@JsonProperty("next_cursor") Long nextCursor, @JsonProperty("per_page") Integer perPage) {
        }

        public Long nextCursor() {
            return meta == null ? null : meta.nextCursor();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlGame(
            long id,
            String date,
            String status,
            Integer period,
            @JsonProperty("home_team") Team homeTeam,
            @JsonProperty("away_team") Team awayTeam,
            @JsonProperty("home_team_data") TeamData homeTeamData,
            @JsonProperty("away_team_data") TeamData awayTeamData
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Team(long id, @JsonProperty("display_name") String displayName, String abbreviation) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TeamData(Integer runs, @JsonProperty("inning_scores") List<Integer> inningScores) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlPlay(
            @JsonProperty("order") Long order,
            String type,
            Integer inning,
            @JsonProperty("inning_type") String inningType,
            String text,
            @JsonProperty("home_score") Integer homeScore,
            @JsonProperty("away_score") Integer awayScore,
            @JsonProperty("scoring_play") Boolean scoringPlay,
            @JsonProperty("score_value") Integer scoreValue,
            Integer outs,
            Integer balls,
            Integer strikes,
            @JsonProperty("batter_id") Long batterId,
            @JsonProperty("pitcher_id") Long pitcherId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlPlateAppearance(
            @JsonProperty("pa_number") Long paNumber,
            @JsonProperty("game_id") Long gameId,
            Integer inning,
            @JsonProperty("half_inning") String halfInning,
            @JsonProperty("batter_id") Long batterId,
            @JsonProperty("pitcher_id") Long pitcherId,
            @JsonProperty("runner_on_first") Boolean runnerOnFirst,
            @JsonProperty("runner_on_second") Boolean runnerOnSecond,
            @JsonProperty("runner_on_third") Boolean runnerOnThird
    ) {
    }
}
