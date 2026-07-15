package com.pulse.api.user.dto;

import java.util.List;

/**
 * 선수 검색 API의 최상위 응답입니다.
 *
 * players:
 * - 검색된 선수 목록
 *
 * complete:
 * - true: 외부 balldontlie 검색이 정상적으로 완료됨
 * - false: 외부 검색 실패로 로컬 players 결과만 반환함
 *
 * players가 빈 배열인 경우에도 complete를 함께 확인해야
 * "실제로 결과가 없음"과 "외부 장애로 완전한 검색을 못 함"을
 * 구분할 수 있습니다.
 */
public record PlayerSearchResultResponse(
        List<PlayerSearchResponse> players,
        boolean complete
) {

    /**
     * null 목록이 JSON null로 노출되지 않도록
     * 항상 빈 배열 또는 불변 목록으로 정리합니다.
     */
    public PlayerSearchResultResponse {
        players = players == null
                ? List.of()
                : List.copyOf(players);
    }

    /**
     * 외부 검색이 정상적으로 완료된 응답입니다.
     */
    public static PlayerSearchResultResponse complete(
            List<PlayerSearchResponse> players
    ) {
        return new PlayerSearchResultResponse(
                players,
                true
        );
    }

    /**
     * 외부 검색 실패 후 로컬 DB 결과로 폴백한 응답입니다.
     */
    public static PlayerSearchResultResponse incomplete(
            List<PlayerSearchResponse> players
    ) {
        return new PlayerSearchResultResponse(
                players,
                false
        );
    }
}
