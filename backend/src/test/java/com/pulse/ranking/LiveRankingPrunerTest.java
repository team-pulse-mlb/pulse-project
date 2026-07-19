package com.pulse.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pulse.domain.Game;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveRankingPrunerTest {

    private final RankingService rankingService = mock(RankingService.class);
    private final LiveRankingPruner pruner = new LiveRankingPruner(rankingService);

    @Test
    void prune_shouldRemoveMissingAndNonLiveMembersAndReturnOnlyLiveGames() {
        Game live = game(1L, Game.STATUS_IN_PROGRESS);
        Game finished = game(2L, Game.STATUS_FINAL);

        List<Game> result = pruner.prune(
                List.of(1L, 2L, 3L),
                Map.of(1L, live, 2L, finished));

        assertThat(result).containsExactly(live);
        verify(rankingService, never()).removeLive(1L);
        verify(rankingService).removeLive(2L);
        verify(rankingService).removeLive(3L);
    }

    private static Game game(long id, String status) {
        Game game = new Game();
        game.setId(id);
        game.setStatus(status);
        return game;
    }
}
