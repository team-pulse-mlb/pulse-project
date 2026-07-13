package com.pulse.api.team.dto;

import com.pulse.domain.Team;

/*
 * 프론트 관심팀 선택 화면에 내려줄 팀 응답 DTO입니다.
 *
 * Entity(Team)를 그대로 응답하지 않고 DTO로 변환하는 이유:
 * - DB 내부 구조가 프론트에 그대로 노출되는 것을 막기 위해
 * - 프론트에서 필요한 값만 명확하게 내려주기 위해
 * - logoUrl처럼 DB에 없는 계산값도 함께 내려줄 수 있기 때문입니다.
 */
public record TeamResponse(

        /*
         * PULSE 서비스에서 관심팀 저장에 사용할 팀 ID입니다.
         *
         * 회원가입 요청의 selectedTeamIds에는 이 값이 들어갑니다.
         */
        Long teamId,

        /*
         * MLB 공식 로고 URL에 사용할 팀 ID입니다.
         */
        Long logoTeamId,

        /*
         * 팀 약어입니다.
         *
         * 예:
         * LAD, NYY, BOS
         */
        String abbreviation,

        /*
         * 화면에 표시할 전체 팀명입니다.
         *
         * 예:
         * Los Angeles Dodgers
         */
        String displayName,

        /*
         * 짧은 팀명입니다.
         *
         * 예:
         * Dodgers
         */
        String shortDisplayName,

        /*
         * 리그 정보입니다.
         *
         * 예:
         * American, National
         */
        String league,

        /*
         * 지구 정보입니다.
         *
         * 예:
         * East, Central, West
         */
        String division,

        /*
         * 프론트에서 바로 img src로 사용할 수 있는 로고 URL입니다.
         *
         * DB에는 URL 전체를 저장하지 않고 logo_team_id만 저장합니다.
         * URL 패턴이 바뀌면 이 메서드만 수정하면 됩니다.
         */
        String logoUrl
) {

    /*
     * Team 엔티티를 TeamResponse DTO로 변환하는 정적 팩토리 메서드입니다.
     */
    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getTeamId(),
                team.getLogoTeamId(),
                team.getAbbreviation(),
                team.getDisplayName(),
                team.getShortDisplayName(),
                team.getLeague(),
                team.getDivision(),
                createLogoUrl(team.getLogoTeamId())
        );
    }

    /*
     * MLB 공식 팀 로고 URL을 생성합니다.
     *
     * 예:
     * logoTeamId = 119
     * → https://www.mlbstatic.com/team-logos/119.svg
     */
    private static String createLogoUrl(Long logoTeamId) {
        if (logoTeamId == null) {
            return null;
        }

        return "https://www.mlbstatic.com/team-logos/"
                + logoTeamId
                + ".svg";
    }
}