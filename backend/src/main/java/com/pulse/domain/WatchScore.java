package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 점수 이력 로그. 신호별 기여 점수(signal_contributions)를 남겨 추천 근거 설명과
 * 가중치 검증(lift 분석)의 재료로 쓴다.
 */
@Entity
@Table(
        name = "watch_scores",
        indexes = @Index(name = "idx_watch_scores_game_computed_at", columnList = "game_id, computed_at")
)
@Getter
@Setter
@NoArgsConstructor
public class WatchScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "computed_at")
    private Instant computedAt;

    @Column(name = "play_order")
    private Long playOrder;

    private Integer inning;

    private String inningType;

    private Integer baseScore;

    private BigDecimal importanceMultiplier;

    private BigDecimal pregameBonus;

    private Integer watchScore;

    /** 신호별 기여 점수 (예: {"late_or_extra": 20.0, "score_gap": 25.0}) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signal_contributions")
    private Map<String, Double> signalContributions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Column(name = "source")
    private String source;

    @Column(name = "backfilled")
    private Boolean backfilled;
}
