package com.pulse.api.user.dto;

import com.pulse.common.client.BdlDtos.BdlPlayer;
import com.pulse.domain.Player;
import com.pulse.domain.Team;
import lombok.Builder;
import lombok.Getter;

/**
 * 관심 선수 설정 화면의 선수 검색 항목입니다.
 *
 * 검색 성공 시에는 외부 balldontlie 응답을 기준으로 만들고,
 * 같은 playerId의 로컬 Player가 있으면 누락 정보만 보조합니다.
 *
 * 외부 검색 실패 시에는 로컬 Player만으로 만들 수 있습니다.
 */
@Getter
@Builder
public class PlayerSearchResponse {

    /**
     * balldontlie 선수 ID입니다.
     *
     * 동명이인이 존재하더라도 playerId는 서로 다르므로,
     * 관심 선수 선택과 저장은 이름이 아니라 이 값으로 처리합니다.
     */
    private Long playerId;

    /**
     * 선수 전체 영문 이름입니다.
     */
    private String fullName;

    /**
     * 선수 포지션입니다.
     */
    private String position;

    /**
     * 현재 소속 팀의 balldontlie 팀 ID입니다.
     */
    private Long teamId;

    /**
     * 현재 소속 팀 전체 이름입니다.
     *
     * 해당 teamId가 로컬 teams 테이블에 없으면 null입니다.
     */
    private String teamName;

    /**
     * 현재 소속 팀 약어입니다.
     */
    private String teamAbbreviation;

    /**
     * 정상 외부 검색 결과를 응답 DTO로 변환합니다.
     *
     * 우선순위:
     * 1. 외부 검색 결과
     * 2. 같은 playerId의 로컬 Player
     *
     * 로컬 데이터는 외부 결과의 누락값만 보조하며,
     * 외부의 정상값을 덮어쓰지 않습니다.
     */
    public static PlayerSearchResponse fromExternal(
            BdlPlayer externalPlayer,
            Player localPlayer,
            Team team
    ) {
        Long teamId = resolveTeamId(
                externalPlayer,
                localPlayer
        );

        return PlayerSearchResponse.builder()
                .playerId(externalPlayer.id())
                .fullName(
                        firstNonBlank(
                                externalPlayer.fullName(),
                                localPlayer == null
                                        ? null
                                        : localPlayer.getFullName()
                        )
                )
                .position(
                        firstNonBlank(
                                externalPlayer.position(),
                                localPlayer == null
                                        ? null
                                        : localPlayer.getPosition()
                        )
                )
                .teamId(teamId)
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

    /**
     * 외부 API 장애 시 로컬 Player를 폴백 응답으로 변환합니다.
     */
    public static PlayerSearchResponse fromLocal(
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

    /**
     * 외부 팀 ID를 우선 사용하고,
     * 외부 응답에 팀이 없을 때만 로컬 값을 보조로 사용합니다.
     */
    private static Long resolveTeamId(
            BdlPlayer externalPlayer,
            Player localPlayer
    ) {
        if (externalPlayer.team() != null
                && externalPlayer.team().id() != null
                && externalPlayer.team().id() > 0) {

            return externalPlayer.team().id();
        }

        return localPlayer == null
                ? null
                : localPlayer.getTeamId();
    }

    /**
     * 첫 번째 값이 비어 있지 않으면 사용하고,
     * 비어 있으면 두 번째 값을 사용합니다.
     */
    private static String firstNonBlank(
            String primary,
            String fallback
    ) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }

        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }

        return null;
    }
}
