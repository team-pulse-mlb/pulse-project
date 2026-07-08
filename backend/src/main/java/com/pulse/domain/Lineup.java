package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 경기 라인업·선발 예상 투수. 경기 전 폴링으로 갱신한다.
 */
@Entity
@Table(
        name = "lineups",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "player_id"}),
        indexes = @Index(name = "idx_lineups_game_id", columnList = "game_id")
)
@Getter
@Setter
@NoArgsConstructor
public class Lineup {

    /** balldontlie 라인업 항목 id를 그대로 PK로 사용한다. */
    @Id
    @Column(name = "lineup_item_id")
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    private Integer battingOrder;

    private String position;

    private Boolean isProbablePitcher;

    private Instant observedAt;

    @Column(name = "source")
    private String source;
}
