package com.pulse.scorer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.WatchScore;
import com.pulse.domain.WatchScoreRepository;
import com.pulse.poller.GameLifecycle;
import com.pulse.ranking.RankingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LiveRankingRebuildRunnerTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final LiveRankingRebuildRunner runner = new LiveRankingRebuildRunner(
            gameRepository,
            watchScoreRepository,
            rankingService
    );

    @Test
    @DisplayName("Redis 랭킹이 비어 있어도 기동 시 라이브 경기의 최신 점수를 복원한다")
    void rebuild_shouldRestoreLatestScoresForLiveGames() {
        Game scoredGame = game(100L);
        Game gameWithoutScore = game(200L);
        WatchScore latestScore = watchScore(82);
        when(gameRepository.findByLifecycleState(GameLifecycle.LIVE.name()))
                .thenReturn(List.of(scoredGame, gameWithoutScore));
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(100L))
                .thenReturn(Optional.of(latestScore));
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(200L))
                .thenReturn(Optional.empty());

        runner.rebuild();

        verify(rankingService).updateLive(100L, 82.0);
        verify(rankingService, never()).updateLive(200L, 0.0);
    }

    @Test
    @DisplayName("재구축 실패는 전파하지 않아 애플리케이션 기동을 막지 않는다")
    void rebuild_shouldContinueStartupWhenRedisUpdateFails() {
        Game game = game(100L);
        when(gameRepository.findByLifecycleState(GameLifecycle.LIVE.name()))
                .thenReturn(List.of(game));
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(100L))
                .thenReturn(Optional.of(watchScore(82)));
        org.mockito.Mockito.doThrow(new IllegalStateException("Redis 연결 실패"))
                .when(rankingService).updateLive(100L, 82.0);

        runner.rebuild();
    }

    private static Game game(long id) {
        Game game = new Game();
        game.setId(id);
        game.setLifecycleState(GameLifecycle.LIVE.name());
        return game;
    }

    private static WatchScore watchScore(int score) {
        WatchScore watchScore = new WatchScore();
        watchScore.setWatchScore(score);
        return watchScore;
    }
}
