package com.pulse.api.home;

import java.time.Instant;

public record RankingScheduledGameCard(
        long gameId,
        MatchupResponse matchup,
        Instant startTime,
        String venue,
        ProbablePitchersResponse probablePitchers
) {
}
