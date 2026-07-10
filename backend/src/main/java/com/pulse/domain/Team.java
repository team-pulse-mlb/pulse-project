package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
public class Team {

    /*
     * PULSE에서 관심팀 선택/경기 데이터 연결에 사용하는 팀 ID입니다.
     *
     * 현재 프로젝트 구조에서는 balldontlie MLB API의 팀 id를 그대로 사용합니다.
     * 따라서 @GeneratedValue를 붙이지 않습니다.
     */
    @Id
    @Column(name = "team_id")
    private Long teamId;

    /*
     * MLB 공식 로고 URL에 사용할 팀 ID입니다.
     *
     * 주의:
     * team_id는 현재 balldontlie API의 팀 ID로 사용하고 있고,
     * MLB 공식 로고 URL에 들어가는 ID는 다른 체계일 수 있습니다.
     *
     * 예:
     * https://www.mlbstatic.com/team-logos/{logo_team_id}.svg
     *
     * 그래서 team_id와 logo_team_id를 분리해서 저장합니다.
     */
    @Column(name = "logo_team_id")
    private Long logoTeamId;

    /*
     * 팀 약어입니다.
     *
     * 예:
     * LAD, NYY, BOS
     */
    @Column(name = "abbreviation", length = 20)
    private String abbreviation;

    /*
     * 화면에 표시할 전체 팀명입니다.
     *
     * 예:
     * Los Angeles Dodgers
     */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /*
     * 화면 공간이 좁을 때 사용할 짧은 팀명입니다.
     *
     * 예:
     * Dodgers
     */
    @Column(name = "short_display_name", length = 100)
    private String shortDisplayName;

    /*
     * 지역명을 제외한 팀명입니다.
     *
     * 예:
     * Dodgers
     */
    @Column(name = "name", length = 100)
    private String name;

    /*
     * 팀 연고지입니다.
     *
     * 예:
     * Los Angeles
     */
    @Column(name = "location", length = 100)
    private String location;

    /*
     * URL 또는 문자열 식별에 사용하기 좋은 값입니다.
     *
     * 예:
     * los-angeles-dodgers
     *
     * 로고가 아닙니다.
     */
    @Column(name = "slug", length = 100)
    private String slug;

    /*
     * 리그 정보입니다.
     *
     * balldontlie 기준 예:
     * American, National
     */
    @Column(name = "league", length = 50)
    private String league;

    /*
     * 지구 정보입니다.
     *
     * 예:
     * East, Central, West
     */
    @Column(name = "division", length = 50)
    private String division;

    private Instant createdAt;

    private Instant updatedAt;

}