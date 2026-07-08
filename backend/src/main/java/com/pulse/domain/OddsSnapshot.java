package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 경기 전 배당 스냅샷. 경기·벤더별 FIRST_SEEN, PREGAME_FINAL 2종만 보존한다.
 */
@Entity
@Table(
        name = "odds_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "vendor", "snapshot_type"})
)
@Getter
@Setter
@NoArgsConstructor
public class OddsSnapshot {

    public static final String SNAPSHOT_FIRST_SEEN = "FIRST_SEEN";
    public static final String SNAPSHOT_PREGAME_FINAL = "PREGAME_FINAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    private String vendor;

    @Column(name = "snapshot_type")
    private String snapshotType;

    private Integer moneylineHomeOdds;
    private Integer moneylineAwayOdds;

    @Column(precision = 3, scale = 1)
    private BigDecimal spreadHomeValue;

    @Column(precision = 3, scale = 1)
    private BigDecimal spreadAwayValue;

    private Integer spreadHomeOdds;
    private Integer spreadAwayOdds;

    @Column(precision = 3, scale = 1)
    private BigDecimal totalValue;

    private Integer totalOverOdds;
    private Integer totalUnderOdds;

    private Instant vendorUpdatedAt;

    private Instant observedAt;

    @Column(name = "source")
    private String source;
}
