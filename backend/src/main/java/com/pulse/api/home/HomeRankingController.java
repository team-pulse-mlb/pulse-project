package com.pulse.api.home;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
@Tag(name = "홈", description = "홈 추천 영역과 전체 경기 목록 조회")
public class HomeRankingController {

    private final HomeQueryService homeQueryService;

    @Operation(
            summary = "홈 추천 경기 조회",
            description = """
                    예정·진행·종료 경기 추천 슬롯을 반환한다.
                    로그인한 요청은 관심 팀·선수 가산이 정렬에 반영될 수 있다.
                    추천 점수와 순위 숫자는 응답하지 않는다.
                    """
    )
    @GetMapping("/live")
    public HomeRankingResponse live(
            @Parameter(description = "상태별 최대 경기 수", example = "5")
            @RequestParam(defaultValue = "5") int count,
            Authentication authentication
    ) {
        return homeQueryService.getRanking(count, username(authentication));
    }

    private static String username(Authentication authentication) {
        return authentication == null || authentication instanceof AnonymousAuthenticationToken
                ? null
                : authentication.getName();
    }
}
