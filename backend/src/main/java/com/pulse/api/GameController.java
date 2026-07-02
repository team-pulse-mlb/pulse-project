package com.pulse.api;

import com.pulse.api.GameQueryService.DisplayMode;
import com.pulse.api.GameQueryService.GameDetailResponse;
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

    @GetMapping("/{gameId}")
    public GameDetailResponse detail(
            @PathVariable long gameId,
            @RequestParam(defaultValue = "PROTECTED") DisplayMode mode
    ) {
        return gameQueryService.getGameDetail(gameId, mode);
    }
}
