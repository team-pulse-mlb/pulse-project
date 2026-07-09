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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 팀 순위 일 배치 스냅샷. 날짜별로 보존한다.
 */
@Entity
@Table(
        name = "standings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"season", "snapshot_date", "team_id"})
)
@Getter
@Setter
@NoArgsConstructor
public class Standing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer season;

    @Column(name = "snapshot_date")
    private LocalDate snapshotDate;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    private String leagueName;

    private String divisionName;

    private Integer wins;

    private Integer losses;

    @Column(precision = 4, scale = 3)
    private BigDecimal winPercent;

    @Column(precision = 4, scale = 1)
    private BigDecimal gamesBehind;

    @Column(precision = 5, scale = 2)
    private BigDecimal playoffPercent;

    @Column(precision = 5, scale = 2)
    private BigDecimal wildcardPercent;

    private Integer streak;

    private String lastTenGames;

    private Instant observedAt;

    @Column(name = "source")
    private String source;
}
