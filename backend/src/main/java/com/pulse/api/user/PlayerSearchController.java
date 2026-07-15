package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
     * 예:
     * GET /api/players?search=Ohtani
     * GET /api/players?search=judge
     *
     * 검색어가 비어 있으면 빈 배열을 반환합니다.
     */
    @GetMapping
    public ResponseEntity<List<PlayerSearchResponse>> searchPlayers(
            @RequestParam(
                    name = "search",
                    defaultValue = ""
            )
            String search
    ) {
        List<PlayerSearchResponse> response =
                playerSearchService.searchPlayers(search);

        return ResponseEntity.ok(response);
    }
}