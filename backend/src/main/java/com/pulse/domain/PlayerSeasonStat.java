package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 선수 시즌 누적 스탯 캐시. 최신값을 덮어쓴다.
 */
@Entity
@Table(name = "player_season_stats")
@IdClass(PlayerSeasonStatId.class)
@Getter
@Setter
@NoArgsConstructor
public class PlayerSeasonStat {

    @Id
    private Integer season;

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Column(precision = 4, scale = 2)
    private BigDecimal pitchingEra;

    @Column(precision = 4, scale = 2)
    private BigDecimal pitchingWar;

    @Column(precision = 4, scale = 2)
    private BigDecimal pitchingWhip;

    @Column(name = "pitching_k_per_9", precision = 4, scale = 2)
    private BigDecimal pitchingKPer9;

    @Column(precision = 4, scale = 2)
    private BigDecimal battingWar;

    @Column(precision = 4, scale = 3)
    private BigDecimal battingOps;

    private Integer battingHr;

    private Instant updatedAt;
}
