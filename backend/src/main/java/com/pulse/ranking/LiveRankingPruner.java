package com.pulse.ranking;

import com.pulse.domain.Game;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class LiveRankingPruner {

    private final RankingService rankingService;

    public LiveRankingPruner(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    public List<Game> prune(List<Long> rankedGameIds, Map<Long, Game> rankedGames) {
        rankedGameIds.stream()
                .filter(gameId -> !Optional.ofNullable(rankedGames.get(gameId))
                        .filter(Game::isLive)
                        .isPresent())
                .forEach(rankingService::removeLive);
        return rankedGameIds.stream()
                .map(rankedGames::get)
                .filter(java.util.Objects::nonNull)
                .filter(Game::isLive)
                .toList();
    }
}
