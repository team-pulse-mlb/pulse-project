package com.pulse.api;

import com.pulse.api.GameQueryService.LlmPurpose;
import com.pulse.api.GameQueryService.SpoilerFreeLlmContextResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/games")
@RequiredArgsConstructor
public class AiContextController {

    private final GameQueryService gameQueryService;

    @Operation(
            summary = "LLM용 스포일러 프리 경기 context 조회",
            description = "ai-service가 문구를 생성할 때 사용할 팀, 구간, reasonTags, 안전한 신호, 최근 play 텍스트를 조회한다."
    )
    @GetMapping("/{gameId}/spoiler-free-context")
    public SpoilerFreeLlmContextResponse spoilerFreeContext(
            @Parameter(description = "balldontlie 경기 ID", example = "5059041")
            @PathVariable long gameId,
            @Parameter(description = "LLM 문구 생성 목적")
            @RequestParam(defaultValue = "FINAL_HEADLINE") LlmPurpose purpose
    ) {
        return gameQueryService.getSpoilerFreeLlmContext(gameId, purpose);
    }
}
