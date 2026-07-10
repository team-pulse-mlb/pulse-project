package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
 * 경기 최신 스냅샷. 폴링할 때마다 덮어쓴다.
 */
@Entity
@Table(name = "games")
@Getter
@Setter
@NoArgsConstructor
public class Game {

    public static final String STATUS_SCHEDULED = "STATUS_SCHEDULED";
    public static final String STATUS_IN_PROGRESS = "STATUS_IN_PROGRESS";
    public static final String STATUS_FINAL = "STATUS_FINAL";
    public static final String STATUS_POSTPONED = "STATUS_POSTPONED";
    public static final String STATUS_CANCELED = "STATUS_CANCELED";

    /** balldontlie 경기 id를 그대로 PK로 사용한다. */
    @Id
    @Column(name = "game_id")
    private Long id;

    private Instant startTime;

    private Boolean postseason;

    @Column(nullable = false)
    private String status;

    private String lifecycleState;

    /** 현재 이닝 */
    private Integer period;

    private Long homeTeamId;
    private String homeTeamName;
    private String homeTeamAbbr;
    private Long awayTeamId;
    private String awayTeamName;
    private String awayTeamAbbr;

    private Integer homeRuns;
    private Integer awayRuns;

    private String venue;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> homeInningScores;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> awayInningScores;

    /** 예정 경기 추천 점수 (0~100). scorer가 PREGAME task 소비 시 덮어쓴다. */
    private Integer pregameScore;

    /** pregame_score 계산 시점 입력 스냅샷(추적용). */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> pregameInputs;

    /** 라이브 중 기록한 최고 base_score. 종료 경기 정렬 기준. */
    private Integer peakBaseScore;

    /** 종료 문구. AI 생성 트리거가 채운다. */
    @Column(columnDefinition = "text")
    private String finalHeadline;

    /** /plays 증분 수집용 마지막 play order */
    private Long lastPlayOrder;

    private Instant lastPolledAt;

    private Instant observedAt;

    private Instant updatedAt;

    private Instant createdAt;

    @Column(name = "source")
    private String source;

    public boolean isLive() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isFinal() {
        return status != null && status.startsWith(STATUS_FINAL);
    }

    public int scoreGap() {
        int home = homeRuns == null ? 0 : homeRuns;
        int away = awayRuns == null ? 0 : awayRuns;
        return Math.abs(home - away);
    }

    public int totalRuns() {
        int home = homeRuns == null ? 0 : homeRuns;
        int away = awayRuns == null ? 0 : awayRuns;
        return home + away;
    }
}
