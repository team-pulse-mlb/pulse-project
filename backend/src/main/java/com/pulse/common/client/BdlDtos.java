package com.pulse.common.client;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
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
            @JsonProperty("away_team_data") TeamData awayTeamData,
            String venue
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
    public record BdlLineup(
            Long id,
            @JsonProperty("game_id") Long gameId,
            Player player,
            TeamRef team,
            @JsonProperty("batting_order") Integer battingOrder,
            String position,
            @JsonProperty("is_probable_pitcher") Boolean isProbablePitcher
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Player(
                Long id,
                @JsonProperty("full_name") @JsonAlias({"display_name", "name"}) String fullName,
                @JsonProperty("first_name") String firstName,
                @JsonProperty("last_name") String lastName,
                @JsonAlias("primary_position") String position
        ) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record TeamRef(Long id) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlOdds(
            @JsonProperty("game_id") Long gameId,
            @JsonAlias({"sportsbook", "book"}) String vendor,
            @JsonProperty("moneyline_home_odds") @JsonAlias({"home_moneyline", "home_ml"}) Integer moneylineHomeOdds,
            @JsonProperty("moneyline_away_odds") @JsonAlias({"away_moneyline", "away_ml"}) Integer moneylineAwayOdds,
            @JsonProperty("spread_home_value") @JsonAlias("home_spread") BigDecimal spreadHomeValue,
            @JsonProperty("spread_away_value") @JsonAlias("away_spread") BigDecimal spreadAwayValue,
            @JsonProperty("spread_home_odds") @JsonAlias("home_spread_odds") Integer spreadHomeOdds,
            @JsonProperty("spread_away_odds") @JsonAlias("away_spread_odds") Integer spreadAwayOdds,
            @JsonProperty("total_value") @JsonAlias("total") BigDecimal totalValue,
            @JsonProperty("total_over_odds") @JsonAlias("over_odds") Integer totalOverOdds,
            @JsonProperty("total_under_odds") @JsonAlias("under_odds") Integer totalUnderOdds,
            @JsonProperty("updated_at") String vendorUpdatedAt
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlStanding(
            Team team,
            @JsonProperty("league_name") String leagueName,
            @JsonProperty("division_name") String divisionName,
            Integer wins,
            Integer losses,
            @JsonProperty("win_percent") BigDecimal winPercent,
            @JsonProperty("games_behind") BigDecimal gamesBehind,
            @JsonProperty("playoff_percent") BigDecimal playoffPercent,
            @JsonProperty("wildcard_percent") BigDecimal wildcardPercent,
            Integer streak,
            @JsonProperty("last_ten_games") String lastTenGames
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Team(Long id) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlPlayerSeasonStat(
            @JsonProperty("player_id") Long playerId,
            Player player,
            Integer season,
            @JsonProperty("pitching_era") @JsonAlias("era") BigDecimal pitchingEra,
            @JsonProperty("pitching_war") BigDecimal pitchingWar,
            @JsonProperty("pitching_whip") @JsonAlias("whip") BigDecimal pitchingWhip,
            @JsonProperty("pitching_k_per_9") @JsonAlias("k_per_9") BigDecimal pitchingKPer9,
            @JsonProperty("batting_war") BigDecimal battingWar,
            @JsonProperty("batting_ops") @JsonAlias("ops") BigDecimal battingOps,
            @JsonProperty("batting_hr") @JsonAlias("hr") Integer battingHr
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Player(Long id) {
        }

        public Long resolvedPlayerId() {
            if (playerId != null) {
                return playerId;
            }
            return player == null ? null : player.id();
        }
    }

    /** /plate_appearances 원본 응답과 파싱 결과. 원본은 S3 아카이브 유지용이다. */
    public record PlateAppearancesRaw(JsonNode response, List<BdlPlateAppearance> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlPlateAppearance(
            @JsonProperty("pa_number") Long paNumber,
            @JsonProperty("game_id") Long gameId,
            Integer inning,
            @JsonProperty("half_inning") String halfInning,
            @JsonProperty("batter_id") Long batterId,
            @JsonProperty("pitcher_id") Long pitcherId,
            Integer outs,
            @JsonProperty("runner_on_first") Boolean runnerOnFirst,
            @JsonProperty("runner_on_second") Boolean runnerOnSecond,
            @JsonProperty("runner_on_third") Boolean runnerOnThird,
            List<BdlPitch> pitches
    ) {

        public BdlPlateAppearance(
                Long paNumber,
                Long gameId,
                Integer inning,
                String halfInning,
                Long batterId,
                Long pitcherId,
                Boolean runnerOnFirst,
                Boolean runnerOnSecond,
                Boolean runnerOnThird
        ) {
            this(
                    paNumber,
                    gameId,
                    inning,
                    halfInning,
                    batterId,
                    pitcherId,
                    null,
                    runnerOnFirst,
                    runnerOnSecond,
                    runnerOnThird,
                    List.of()
            );
        }

        public BdlPlateAppearance {
            pitches = pitches == null ? List.of() : List.copyOf(pitches);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BdlPitch(
            @JsonProperty("pitch_number") Integer pitchNumber,
            @JsonProperty("pitcher_pitch_count") Integer pitcherPitchCount,
            @JsonProperty("release_speed") Double releaseSpeed,
            @JsonProperty("exit_velocity") Double exitVelocity,
            @JsonProperty("is_barrel") Boolean isBarrel
    ) {
    }
}
