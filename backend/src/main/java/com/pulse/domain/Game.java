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

    /** 종료 보호 모드 헤드라인. 결과를 드러내지 않는 AI 문구다. */
    @Column(columnDefinition = "text")
    private String finalHeadlineProtected;

    /** 종료 공개 모드 헤드라인. 최종 점수와 승패를 반영한 AI 문구다. */
    @Column(columnDefinition = "text")
    private String finalHeadlineRevealed;

    /**
     * 중요 플레이 번역 저장 후 REVEALED 헤드라인의
     * 일회성 자동 재생성을 시도한 시각입니다.
     *
     * Redis나 scorer 프로세스 재시작과 무관하게
     * 중복 AI 호출을 방지하기 위해 DB에 저장합니다.
     */
    @Column(
            name = "final_headline_revealed_regeneration_attempted_at"
    )
    private Instant finalHeadlineRevealedRegenerationAttemptedAt;

    /** /plays 증분 수집용 마지막 play order */
    private Long lastPlayOrder;

    private Instant lastPolledAt;

    private Instant observedAt;

    /** FINAL 경기의 일회성 종료 후처리를 최초 확정한 시각. */
    private Instant finalizedAt;

    /** DONE terminal task를 최초 처리한 시각. */
    private Instant terminalDoneAt;

    /** SUSPENDED_POSTPONED terminal task를 최초 처리한 시각. */
    private Instant terminalSuspendedPostponedAt;

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
