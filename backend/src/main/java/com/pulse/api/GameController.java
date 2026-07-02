package com.pulse.api;

import com.pulse.api.GameQueryService.DisplayMode;
import com.pulse.api.GameQueryService.GameDetailResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

    private final GameQueryService gameQueryService;

    @Operation(
            summary = "경기 상세 조회",
            description = "경기 스냅샷, 최신 추천 점수, 최근 play를 경기 상세 화면용으로 조회한다."
    )
    @GetMapping("/{gameId}")
    public GameDetailResponse detail(
            @Parameter(description = "balldontlie 경기 ID", example = "5059041")
            @PathVariable long gameId,
            @Parameter(description = "PROTECTED는 스포일러 값을 숨기고, NORMAL은 점수와 득점 play 정보를 포함한다.")
            @RequestParam(defaultValue = "PROTECTED") DisplayMode mode
    ) {
        return gameQueryService.getGameDetail(gameId, mode);
    }
}
