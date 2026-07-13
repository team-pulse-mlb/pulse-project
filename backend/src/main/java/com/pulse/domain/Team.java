package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * balldontlie 팀 마스터. poller가 games FK를 만족시키기 위해 먼저 upsert한다.
 * 마스터 상세 컬럼(연고지, 리그, 지구, 로고 id)은 V7 시드로 채우고,
 * poller는 abbreviation과 displayName만 갱신한다.
 */
@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    /** balldontlie 팀 id. 외부 발급이라 @GeneratedValue를 쓰지 않는다. */
    @Id
    @Column(name = "team_id")
    private Long teamId;

    /** MLB 공식 로고 URL({id}.svg)용 팀 id. balldontlie id와 체계가 달라 분리 저장한다. */
    @Column(name = "logo_team_id")
    private Long logoTeamId;

    private String abbreviation;

    private String displayName;

    private String shortDisplayName;

    private String name;

    private String location;

    private String slug;

    private String league;

    private String division;

    private Instant createdAt;

    private Instant updatedAt;
}
