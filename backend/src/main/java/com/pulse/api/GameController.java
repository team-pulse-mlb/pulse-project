package com.pulse.api;

import com.pulse.api.GameQueryService.GameDetailView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController{

    private final GameQueryService gameQueryService;

    @Operation(
            summary = "경기 상세 조회",
            description = "경기 스냅샷, 최신 추천 점수, 최근 play를 경기 상세 화면용으로 조회한다."
    )
    @GetMapping("/{gameId}")
    public GameDetailView detail(
            @Parameter(description = "balldontlie 경기 ID", example = "5059041")
            @PathVariable long gameId,

            // 기본값은 반드시 protected로 둔다.
            // 사용자가 mode를 생략해도 스포일러 보호 응답이 내려가야 한다.

            // Spring이 enum을 직접 바인딩하면 "protected" 같은 소문자 요청이 400이 된다.
            // 프론트와 사용자는 보통 소문자 쿼리스트링을 사용하므로,
            // 서비스에서 대소문자를 흡수해 안전하게 DisplayMode로 변환한다.
            @Parameter(description = "protected는 스포일러 값을 숨기고, revealed는 점수와 득점 play 정보를 포함한다.")
            @RequestParam(defaultValue = "protected") String mode
    ) {
        return gameQueryService.getGameDetail(gameId, mode);
    }
}
