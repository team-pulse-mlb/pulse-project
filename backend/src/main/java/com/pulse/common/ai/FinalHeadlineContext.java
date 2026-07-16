package com.pulse.common.ai;

import java.util.List;

public record FinalHeadlineContext(
        long gameId,
        AiCopyMode mode,
        String status,
        String periodLabel,
        List<String> reasonTags,
        List<String> spoilerSafeSignals,
        List<KeyMoment> keyMoments,
        Teams teams,
        FinalScore finalScore,
        String winner,
        Integer inningsPlayed,
        Boolean extraInnings,
        Boolean postseason,
        List<RevealedMoment> revealedMoments,
        String contextHash
) {
    /** 기존 보호 컨텍스트 생성 호출과의 호환용 생성자다. */
    public FinalHeadlineContext(
            long gameId,
            AiCopyMode mode,
            String status,
            String periodLabel,
            List<String> reasonTags,
            List<String> spoilerSafeSignals,
            List<KeyMoment> keyMoments,
            FinalScore finalScore,
            String winner,
            String contextHash
    ) {
        this(gameId, mode, status, periodLabel, reasonTags, spoilerSafeSignals, keyMoments,
                null, finalScore, winner, null, null, null, List.of(), contextHash);
    }

    public record KeyMoment(Integer inning, String label) {
    }

    public record Teams(Team home, Team away) {
    }

    public record Team(String name, String abbr) {
    }

    public record FinalScore(Integer home, Integer away) {
    }

    public record RevealedMoment(
            Integer inning,
            String inningHalf,
            String battingTeam,
            List<String> eventTypes,
            String batter,
            Integer runsScored,
            ScoreAfter scoreAfter,
            Long scoringPlays
    ) {
        public RevealedMoment {
            eventTypes = eventTypes == null ? List.of() : List.copyOf(eventTypes);
        }
    }

    public record ScoreAfter(Integer home, Integer away) {
    }
}
