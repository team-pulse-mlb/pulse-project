package com.pulse.api.user;

import com.pulse.api.user.dto.PlayerSearchResultResponse;
import com.pulse.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "선수", description = "관심 선수 등록을 위한 선수 검색")
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
    @Operation(
            summary = "선수 이름 검색",
            description = """
                    balldontlie 이름 검색 결과를 우선 반환하며 검색 과정에서 DB를 변경하지 않는다.
                    complete=false이면 외부 조회 실패로 로컬 DB의 불완전한 결과만 반환한 상태다.
                    """,
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
    @GetMapping
    public ResponseEntity<PlayerSearchResultResponse> searchPlayers(
            @Parameter(description = "선수 영문 이름 검색어", example = "Ohtani")
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
