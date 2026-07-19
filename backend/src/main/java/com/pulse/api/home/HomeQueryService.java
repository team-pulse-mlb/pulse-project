package com.pulse.api.home;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HomeQueryService {

    private static final int HOME_RANKING_LIMIT = 5;

    private final HomeRankingReader homeRankingReader;
    private final HomeSlateReader homeSlateReader;
    private final AnonymousHomeRankingCache anonymousRankingCache;

    public HomeRankingResponse getRanking(int count) {
        return getRanking(count, null);
    }

    public HomeRankingResponse getRanking(int count, String username) {
        int safeCount = rankingLimit(count);
        if (username == null || username.isBlank()) {
            return anonymousRankingCache.get(safeCount, () -> homeRankingReader.loadRanking(safeCount, null));
        }
        return homeRankingReader.loadRanking(safeCount, username);
    }

    public HomeSlateResponse getSlate(String date, String status, String sort) {
        return getSlate(date, status, sort, null);
    }

    public HomeSlateResponse getSlate(String date, String status, String sort, String username) {
        return homeSlateReader.getSlate(date, status, sort, username);
    }

    private static int rankingLimit(int count) {
        if (count <= 0) {
            return HOME_RANKING_LIMIT;
        }
        return Math.min(count, HOME_RANKING_LIMIT);
    }
}
