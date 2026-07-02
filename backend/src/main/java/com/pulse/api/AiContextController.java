package com.pulse.api;

import com.pulse.api.GameQueryService.LlmPurpose;
import com.pulse.api.GameQueryService.SpoilerFreeLlmContextResponse;
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

    @GetMapping("/{gameId}/spoiler-free-context")
    public SpoilerFreeLlmContextResponse spoilerFreeContext(
            @PathVariable long gameId,
            @RequestParam(defaultValue = "CARD_SUMMARY") LlmPurpose purpose
    ) {
        return gameQueryService.getSpoilerFreeLlmContext(gameId, purpose);
    }
}
