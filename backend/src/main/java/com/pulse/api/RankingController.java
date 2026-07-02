package com.pulse.api;

import com.pulse.scorer.RankingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 개발 확인용 랭킹 조회. 파이프라인(폴링 → 점수 계산 → 랭킹)이 도는지 확인하는 용도이며,
 * 사용자 노출용 홈 추천 API(스포일러 보호 DTO 포함)는 별도로 구현한다.
 */
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    public record RankingEntry(long gameId, double watchScore) {
    }

    @GetMapping("/live")
    public List<RankingEntry> live(@RequestParam(defaultValue = "20") int count) {
        return rankingService.topLive(count).entrySet().stream()
                .map(e -> new RankingEntry(e.getKey(), e.getValue()))
                .toList();
    }
}
