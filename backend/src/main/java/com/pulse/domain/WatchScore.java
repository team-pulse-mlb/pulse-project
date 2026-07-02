package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 점수 이력 로그. 신호별 기여 점수(signals)를 남겨 추천 근거 설명과
 * 가중치 검증(lift 분석)의 재료로 쓴다.
 */
@Entity
@Table(name = "watch_scores", indexes = @Index(name = "idx_watch_scores_game", columnList = "game_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class WatchScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    private double baseScore;

    private double watchScore;

    /** 신호별 기여 점수 (예: {"late_or_extra": 20.0, "score_gap": 25.0}) */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Double> signals;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> reasonTags;

    /** 계산에 사용한 scoring.yml version */
    private int configVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
