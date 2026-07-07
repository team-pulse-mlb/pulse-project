package com.pulse.api.home;

import com.pulse.api.home.HomeQueryService.HomeRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class HomeRankingController {

    private final HomeQueryService homeQueryService;

    @GetMapping("/live")
    public HomeRankingResponse live(@RequestParam(defaultValue = "20") int count) {
        return homeQueryService.getRanking(count);
    }
}
