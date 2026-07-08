package com.pulse.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * player_season_stats 복합 키 (season, player_id).
 */
public class PlayerSeasonStatId implements Serializable {

    private Integer season;
    private Long playerId;

    public PlayerSeasonStatId() {
    }

    public PlayerSeasonStatId(Integer season, Long playerId) {
        this.season = season;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PlayerSeasonStatId that)) {
            return false;
        }
        return Objects.equals(season, that.season) && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(season, playerId);
    }
}
