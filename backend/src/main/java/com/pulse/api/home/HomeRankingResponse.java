package com.pulse.api.home;

import java.time.Instant;
import java.util.List;

public record HomeRankingResponse(
        Instant generatedAt,
        List<RankingLiveGameCard> live,
        List<RankingScheduledGameCard> scheduled,
        List<RankingFinishedGameCard> finished
) {
}
