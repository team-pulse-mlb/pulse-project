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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 라이브 계산 중 열린 다시보기 추천 구간.
 */
@Entity
@Table(name = "replay_segments", indexes = @Index(name = "idx_replay_segments_game", columnList = "game_id"))
@Getter
@Setter
@NoArgsConstructor
public class ReplaySegment {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    private Long startPlayOrder;
    private Long endPlayOrder;

    private Integer startInning;
    private String startInningType;
    private Integer endInning;
    private String endInningType;

    private Integer peakScore;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Column(columnDefinition = "text")
    private String aiSummary;

    private String status;

    private Instant openedAt;

    private Instant closedAt;

    @Column(name = "source")
    private String source;
}
