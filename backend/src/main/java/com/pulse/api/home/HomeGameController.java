package com.pulse.api.home;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "홈", description = "홈 추천 영역과 전체 경기 목록 조회")
public class HomeGameController {

    private final HomeQueryService homeQueryService;

    @Operation(
            summary = "홈 전체 경기 목록 조회",
            description = """
                    한국 시간(KST) 슬레이트를 기준으로 경기 목록을 조회한다.
                    scheduled 상태는 date와 무관하게 현재 이후의 모든 예정 경기를 반환한다.
                    로그인한 요청은 관심 팀·선수 가산이 정렬에 반영될 수 있다.
                    정렬 기준을 생략하면 scheduled는 startTime, 나머지 상태는 recommended를 적용한다.
                    """
    )
    @GetMapping
    public HomeSlateResponse slate(
            @Parameter(description = "KST 슬레이트 날짜", example = "2026-07-18")
            @RequestParam(required = false) String date,

            @Parameter(
                    description = "조회할 경기 상태",
                    schema = @Schema(
                            allowableValues = {"all", "scheduled", "live", "finished"},
                            defaultValue = "all"
                    )
            )
            @RequestParam(defaultValue = "all") String status,

            @Parameter(
                    description = "정렬 기준. 생략 시 scheduled는 startTime, 나머지 상태는 recommended를 적용한다. recommended는 상태별 점수를 사용하지만 점수 자체는 응답하지 않는다.",
                    schema = @Schema(
                            allowableValues = {"recommended", "startTime"}
                    )
            )
            @RequestParam(required = false) String sort,
            Authentication authentication
    ) {
        return homeQueryService.getSlate(date, status, sort, username(authentication));
    }

    private static String username(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
