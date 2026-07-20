package com.pulse.api.home;

public record RankingFinishedGameCard(
        long gameId,
        MatchupResponse matchup,
        String headline,
        String keyMoment
) {
}
