package com.pulse.api.home;

import java.time.Instant;

public record SlateLiveGameCard(
        long gameId,
        String gameState,
        MatchupResponse matchup,
        Instant startTime,
        Integer inning,
        String latestTag
) implements SlateGameCard {
}
