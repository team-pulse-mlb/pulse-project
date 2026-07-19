package com.pulse.api.home;

import java.time.Instant;

public record SlateScheduledGameCard(
        long gameId,
        String gameState,
        MatchupResponse matchup,
        Instant startTime,
        String venue,
        ProbablePitchersResponse probablePitchers
) implements SlateGameCard {
}
