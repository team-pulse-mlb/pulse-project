package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 흥미로운 순간 이벤트(확정 결과). scorer가 라이브 계산 중 임계 통과분을 append한다.
 * 종료 후 재계산하지 않고 상세 타임라인의 원천으로 그대로 재사용한다.
 */
@Entity
@Table(
        name = "game_events",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"game_id", "event_type", "source_type", "source_ref"}
        ),
        indexes = @Index(name = "idx_game_events_game_observed_at", columnList = "game_id, observed_at")
)
@Getter
@Setter
@NoArgsConstructor
public class GameEvent {

    public static final String SPOILER_PROTECTED_SAFE = "PROTECTED_SAFE";
    public static final String SPOILER_REVEALED_ONLY = "REVEALED_ONLY";
    public static final String SOURCE_TYPE_PLAY = "PLAY";
    public static final String SOURCE_TYPE_PA = "PA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "spoiler_level")
    private String spoilerLevel;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_ref", nullable = false)
    private Long sourceRef;

    private Integer inning;

    @Column(name = "inning_type")
    private String inningType;

    @Column(name = "batter_id")
    private Long batterId;

    @Column(name = "pitcher_id")
    private Long pitcherId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> payload;

    @Column(name = "ruleset_version")
    private String rulesetVersion;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "backfilled")
    private Boolean backfilled;

    @Column(name = "source")
    private String source;
}
