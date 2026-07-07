package com.pulse.api.home;

import com.pulse.api.home.HomeQueryService.HomeSlateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class HomeGameController {

    private final HomeQueryService homeQueryService;

    @GetMapping
    public HomeSlateResponse slate(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "startTime") String sort
    ) {
        return homeQueryService.getSlate(date, status, sort);
    }
}
