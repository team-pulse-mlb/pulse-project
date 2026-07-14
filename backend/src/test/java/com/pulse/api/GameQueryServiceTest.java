package com.pulse.api;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.WatchScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameQueryServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final WatchScoreRepository watchScoreRepository = mock(WatchScoreRepository.class);
    private final GameQueryService service = new GameQueryService(
            gameRepository,
            playRepository,
            watchScoreRepository
    );

    @Test
    void getGameDetail_shouldReturnProtectedFinalHeadlineInProtectedMode() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("스포일러 없이 긴장감이 이어진 경기");
        game.setFinalHeadlineRevealed("홈팀이 5-3으로 승리한 경기");

        givenGameDetailDependencies(game);

        GameQueryService.GameDetailView result = service.getGameDetail(100L, "protected");

        assertThat(result).isInstanceOf(GameQueryService.ProtectedGameDetailResponse.class);

        GameQueryService.ProtectedGameDetailResponse response =
                (GameQueryService.ProtectedGameDetailResponse) result;

        assertThat(response.headline()).isEqualTo("스포일러 없이 긴장감이 이어진 경기");
        assertThat(response.displayMode()).isEqualTo(GameQueryService.DisplayMode.PROTECTED);
    }

    @Test
    void getGameDetail_shouldReturnRevealedFinalHeadlineInRevealedMode() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("스포일러 없이 긴장감이 이어진 경기");
        game.setFinalHeadlineRevealed("홈팀이 5-3으로 승리한 경기");

        givenGameDetailDependencies(game);

        GameQueryService.GameDetailView result = service.getGameDetail(100L, "revealed");

        assertThat(result).isInstanceOf(GameQueryService.RevealedGameDetailResponse.class);

        GameQueryService.RevealedGameDetailResponse response =
                (GameQueryService.RevealedGameDetailResponse) result;

        assertThat(response.headline()).isEqualTo("홈팀이 5-3으로 승리한 경기");
        assertThat(response.displayMode()).isEqualTo(GameQueryService.DisplayMode.REVEALED);
    }

    @Test
    void getGameDetail_shouldReturnNullHeadlineWhenStoredHeadlineIsBlank() {
        Game game = finalGame();
        game.setFinalHeadlineProtected("   ");
        game.setFinalHeadlineRevealed("   ");

        givenGameDetailDependencies(game);

        GameQueryService.GameDetailView protectedResult = service.getGameDetail(100L, "protected");
        GameQueryService.GameDetailView revealedResult = service.getGameDetail(100L, "revealed");

        GameQueryService.ProtectedGameDetailResponse protectedResponse =
                (GameQueryService.ProtectedGameDetailResponse) protectedResult;
        GameQueryService.RevealedGameDetailResponse revealedResponse =
                (GameQueryService.RevealedGameDetailResponse) revealedResult;

        assertThat(protectedResponse.headline()).isNull();
        assertThat(revealedResponse.headline()).isNull();
    }

    private void givenGameDetailDependencies(Game game) {
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(watchScoreRepository.findTopByGameIdOrderByComputedAtDesc(100L))
                .thenReturn(Optional.empty());
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                eq(100L),
                any(PageRequest.class)
        )).thenReturn(List.of());
    }

    private static Game finalGame() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_FINAL);
        game.setStartTime(Instant.parse("2026-07-14T10:00:00Z"));
        game.setPeriod(9);
        game.setHomeTeamId(1L);
        game.setHomeTeamName("Home Team");
        game.setHomeTeamAbbr("HOM");
        game.setAwayTeamId(2L);
        game.setAwayTeamName("Away Team");
        game.setAwayTeamAbbr("AWY");
        game.setHomeRuns(5);
        game.setAwayRuns(3);
        return game;
    }
}
