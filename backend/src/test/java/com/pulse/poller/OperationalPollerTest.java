package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OperationalPollerTest {

    private final BalldontlieClient balldontlieClient = mock(BalldontlieClient.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final PollerGameWriter gameWriter = mock(PollerGameWriter.class);
    private final ScoreTaskPublisher scoreTaskPublisher = mock(ScoreTaskPublisher.class);
    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final Instant now = Instant.parse("2026-07-08T00:00:00Z");
    private final OperationalPoller poller = new OperationalPoller(
            balldontlieClient,
            gameRepository,
            playRepository,
            gameWriter,
            new ScoreTaskFactory(),
            scoreTaskPublisher,
            notificationEventPublisher,
            properties(),
            new PollerRateLimiter(1000, Clock.fixed(now, ZoneOffset.UTC)),
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void poll_shouldPublishGameStartAndLiveTaskAfterRunnerStateUpdate() {
        Game liveGame = game(GameLifecycle.LIVE.name(), 1L);
        BdlPlay fetchedPlay = play(1L, 10L);
        Play latestPlay = persistedPlay(1L, 10L, true, true, false);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(new PollerGameWriter.GameUpsertResult(
                        liveGame,
                        GameLifecycle.SCHEDULED.name(),
                        GameLifecycle.LIVE.name(),
                        false
                ));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenReturn(new ListResponse<>(List.of(fetchedPlay), new ListResponse.Meta(null, 100)));
        when(gameWriter.appendPlay(liveGame, fetchedPlay, now)).thenReturn(true);
        when(balldontlieClient.getPlateAppearances(100L))
                .thenReturn(List.of(plateAppearance(1L, 10L)));
        when(gameWriter.updateRunnerStates(eq(100L), any()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(100L, org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(latestPlay));

        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(notificationEventPublisher).publish(any());
        verify(gameWriter).updateRunnerStates(eq(100L), any());
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().situation().basesLoaded()).isFalse();
        assertThat(taskCaptor.getValue().situation().scoringPosition()).isTrue();
    }

    @Test
    void poll_shouldPublishTerminalTaskOnlyWhenLiveGameLeavesLiveState() {
        Game finalGame = game(GameLifecycle.FINAL.name(), 7L);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(Game.STATUS_FINAL)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(new PollerGameWriter.GameUpsertResult(
                        finalGame,
                        GameLifecycle.LIVE.name(),
                        GameLifecycle.FINAL.name(),
                        true
                ));

        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().lifecycleState()).isEqualTo(GameLifecycle.FINAL.name());
        assertThat(taskCaptor.getValue().situation()).isNull();
    }

    private static PollerProperties properties() {
        return new PollerProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofMinutes(10),
                0,
                0,
                5,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                1000,
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(30),
                10
        );
    }

    private static Game game(String lifecycle, Long lastPlayOrder) {
        Game game = new Game();
        game.setId(100L);
        game.setLifecycleState(lifecycle);
        game.setLastPlayOrder(lastPlayOrder);
        return game;
    }

    private static Play persistedPlay(Long order, Long batterId, boolean first, boolean second, boolean third) {
        Play play = new Play();
        play.setGameId(100L);
        play.setPlayOrder(order);
        play.setBatterId(batterId);
        play.setOuts(1);
        play.setBalls(2);
        play.setStrikes(1);
        play.setRunnerOnFirst(first);
        play.setRunnerOnSecond(second);
        play.setRunnerOnThird(third);
        return play;
    }

    private static BdlGame gameDto(String status) {
        return new BdlGame(100L, "2026-07-08T00:00:00Z", status, 1, null, null, null, null);
    }

    private static BdlPlay play(Long order, Long batterId) {
        return new BdlPlay(order, "at_bat", 1, "Top", "play", 0, 0, false, 0, 1, 2, 1, batterId, 99L);
    }

    private static BdlPlateAppearance plateAppearance(Long number, Long batterId) {
        return new BdlPlateAppearance(number, 100L, 1, "top", batterId, 99L, true, true, false);
    }
}
