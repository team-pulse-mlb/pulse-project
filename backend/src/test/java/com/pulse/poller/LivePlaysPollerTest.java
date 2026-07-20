package com.pulse.poller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BaseballDataSource;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.domain.Game;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class LivePlaysPollerTest {

    private final BaseballDataSource baseballDataSource = mock(BaseballDataSource.class);
    private final PollerGameWriter gameWriter = mock(PollerGameWriter.class);
    private final LiveGameCycleWriter liveGameCycleWriter = mock(LiveGameCycleWriter.class);
    private final PollerRateLimiter rateLimiter = mock(PollerRateLimiter.class);
    private final PaRawArchiveUploader paRawArchiveUploader = mock(PaRawArchiveUploader.class);
    private final PollerProperties properties = properties();
    private final LivePlaysPoller poller = new LivePlaysPoller(
            baseballDataSource,
            gameWriter,
            liveGameCycleWriter,
            properties,
            rateLimiter,
            paRawArchiveUploader,
            new PollerLiveGameStateTracker(properties)
    );
    private final Instant now = Instant.parse("2026-07-08T00:00:00Z");

    @AfterEach
    void tearDown() {
        poller.shutdown();
    }

    @Test
    void pollLiveGames_shouldProcessEveryGame() {
        when(baseballDataSource.getPlays(anyLong(), nullable(Long.class)))
                .thenReturn(new ListResponse<BdlPlay>(List.of(), new ListResponse.Meta(null, 100)));
        List<Game> games = List.of(game(1L), game(2L), game(3L), game(4L), game(5L), game(6L), game(7L));

        poller.pollLiveGames(games, now);

        for (Game game : games) {
            verify(baseballDataSource).getPlays(game.getId(), null);
        }
    }

    @Test
    void pollLiveGames_shouldKeepBackoffActiveAfterWorkerFailure() {
        Game failedGame = game(1L);
        Game nextGame = game(2L);
        when(baseballDataSource.getPlays(failedGame.getId(), null))
                .thenThrow(new ResourceAccessException("timeout"));

        poller.pollLiveGames(List.of(failedGame), now);
        poller.pollLiveGames(List.of(nextGame), now);

        verify(baseballDataSource, never()).getPlays(nextGame.getId(), null);
    }

    private static Game game(long gameId) {
        Game game = new Game();
        game.setId(gameId);
        return game;
    }

    private static PollerProperties properties() {
        return new PollerProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofSeconds(75),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(15),
                Duration.ofSeconds(20),
                0,
                0,
                9,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                1000,
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                10,
                new PollerProperties.PaArchive(null, null)
        );
    }
}
