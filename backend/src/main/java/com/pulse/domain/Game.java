package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
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

    /** balldontlie 경기 id를 그대로 PK로 사용한다. */
    @Id
    private Long id;

    private Instant startTime;

    @Column(nullable = false)
    private String status;

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

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> homeInningScores;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<Integer> awayInningScores;

    /** /plays 증분 수집용 커서 (마지막 응답의 next_cursor) */
    private Long playsCursor;

    private Instant updatedAt;

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
