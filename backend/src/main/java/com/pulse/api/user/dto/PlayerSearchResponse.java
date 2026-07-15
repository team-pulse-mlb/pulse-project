package com.pulse.api.user.dto;

import com.pulse.domain.Player;
import com.pulse.domain.Team;
import lombok.Builder;
import lombok.Getter;

/**
 * 관심 선수 설정 화면의 선수 검색 결과입니다.
 *
 * players 테이블의 선수 정보와,
 * team_id로 찾은 teams 테이블 정보를 함께 내려줍니다.
 */
@Getter
@Builder
public class PlayerSearchResponse {

    /**
     * balldontlie 선수 ID입니다.
     *
     * 관심 선수 저장 요청의 selectedPlayerIds에
     * 이 값을 넣어야 합니다.
     */
    private Long playerId;

    /**
     * 선수 전체 영문 이름입니다.
     *
     * 예:
     * Shohei Ohtani
     */
    private String fullName;

    /**
     * 선수 포지션입니다.
     *
     * 아직 선수 보강이 끝나지 않은 데이터라면
     * null일 수 있습니다.
     */
    private String position;

    /**
     * 현재 소속 팀의 balldontlie 팀 ID입니다.
     */
    private Long teamId;

    /**
     * 현재 소속 팀 전체 이름입니다.
     *
     * 선수의 teamId가 없거나 teams 테이블에서 찾지 못하면
     * null로 응답합니다.
     */
    private String teamName;

    /**
     * 현재 소속 팀 약어입니다.
     *
     * 예:
     * LAD, NYY
     */
    private String teamAbbreviation;

    /**
     * Player와 Team 엔티티를 검색 응답으로 변환합니다.
     */
    public static PlayerSearchResponse from(
            Player player,
            Team team
    ) {
        return PlayerSearchResponse.builder()
                .playerId(player.getId())
                .fullName(player.getFullName())
                .position(player.getPosition())
                .teamId(player.getTeamId())
                .teamName(
                        team == null
                                ? null
                                : team.getDisplayName()
                )
                .teamAbbreviation(
                        team == null
                                ? null
                                : team.getAbbreviation()
                )
                .build();
    }
}