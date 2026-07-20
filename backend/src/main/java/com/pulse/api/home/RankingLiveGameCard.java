package com.pulse.api.home;

public record RankingLiveGameCard(
        long gameId,
        MatchupResponse matchup,
        Integer inning,
        String latestTag
) {
}
