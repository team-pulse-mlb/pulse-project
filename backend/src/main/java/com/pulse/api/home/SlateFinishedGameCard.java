package com.pulse.api.home;

import java.time.Instant;

public record SlateFinishedGameCard(
        long gameId,
        String gameState,
        MatchupResponse matchup,
        Instant startTime,
        String headline,
        String keyMoment
) implements SlateGameCard {
}
