package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResultResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관심 선수 등록 화면에서 사용하는 선수 검색 API입니다.
 *
 * API 계약:
 * GET /api/players?search=Ohtani
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
public class PlayerSearchController {

    private final PlayerSearchService playerSearchService;

    /**
     * 선수 영문 이름을 검색합니다.
     *
     * 응답 예:
     * {
     *   "players": [...],
     *   "complete": true
     * }
     *
     * complete=false이면 외부 API 장애로
     * 로컬 DB의 불완전한 검색 결과만 반환한 상태입니다.
     */
    @GetMapping
    public ResponseEntity<PlayerSearchResultResponse> searchPlayers(
            @RequestParam(
                    name = "search",
                    defaultValue = ""
            )
            String search
    ) {
        PlayerSearchResultResponse response =
                playerSearchService.searchPlayers(search);

        return ResponseEntity.ok(response);
    }
}
