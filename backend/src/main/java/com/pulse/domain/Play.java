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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 플레이 append 로그. 흐름 재생과 가중치 검증의 재료가 된다.
 */
@Entity
@Table(
        name = "plays",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_id", "play_order"}),
        indexes = @Index(name = "idx_plays_game_order", columnList = "game_id, play_order")
)
@Getter
@Setter
@NoArgsConstructor
public class Play {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    /** balldontlie play order (큰 정수 ID, 경기 내 순서 보장) */
    @Column(name = "play_order", nullable = false)
    private Long playOrder;

    private String type;

    private Integer inning;

    private String inningType;

    @Column(columnDefinition = "text")
    private String text;

    private Integer homeScore;
    private Integer awayScore;

    private Boolean scoringPlay;
    private Integer scoreValue;

    private Integer outs;
    private Integer balls;
    private Integer strikes;

    @Column(name = "batter_id")
    private Long batterId;

    @Column(name = "pitcher_id")
    private Long pitcherId;

    /** 수집 시각. play 자체에 타임스탬프가 없어 시간 감쇠 계산의 기준으로 쓴다. */
    @Column(name = "observed_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "source")
    private String source;

    @Column(name = "backfilled")
    private Boolean backfilled;

    @Column(name = "runner_on_first")
    private Boolean runnerOnFirst;

    @Column(name = "runner_on_second")
    private Boolean runnerOnSecond;

    @Column(name = "runner_on_third")
    private Boolean runnerOnThird;
}
