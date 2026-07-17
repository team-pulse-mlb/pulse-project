package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos;
import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.common.client.BdlDtos.ListResponse;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class OperationalPollerTest {

    private final BalldontlieClient balldontlieClient = mock(BalldontlieClient.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final PollerGameWriter gameWriter = mock(PollerGameWriter.class);
    private final ScoreTaskPublisher scoreTaskPublisher = mock(ScoreTaskPublisher.class);
    private final LiveGameCycleWriter liveGameCycleWriter = new LiveGameCycleWriter(
            gameWriter,
            playRepository,
            new ScoreTaskFactory(),
            scoreTaskPublisher
    );
    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final PaRawArchiveUploader paRawArchiveUploader = mock(PaRawArchiveUploader.class);
    private final Instant now = Instant.parse("2026-07-08T00:00:00Z");
    private final OperationalPoller poller = new OperationalPoller(
            balldontlieClient,
            gameRepository,
            gameWriter,
            liveGameCycleWriter,
            new ScoreTaskFactory(),
            scoreTaskPublisher,
            notificationEventPublisher,
            properties(),
            new PollerRateLimiter(1000, Clock.fixed(now, ZoneOffset.UTC)),
            paRawArchiveUploader,
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
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of(plateAppearance(1L, 10L))));
        when(gameWriter.updateRunnerStates(eq(100L), any()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(100L, org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(latestPlay));

        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        ArgumentCaptor<NotificationEvent> notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventPublisher).publish(notificationCaptor.capture());
        verify(gameWriter).updateRunnerStates(eq(100L), any());
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        assertThat(notificationCaptor.getValue().type()).isEqualTo(NotificationEvent.NotificationType.GAME_START);
        assertThat(taskCaptor.getValue().plateAppearances()).hasSize(1);
        assertThat(taskCaptor.getValue().situation().basesLoaded()).isFalse();
        assertThat(taskCaptor.getValue().situation().scoringPosition()).isTrue();
    }

    @Test
    void poll_shouldDrainRemainingPlaysBeforePublishingTerminalTask() {
        Game finalGame = game(GameLifecycle.FINAL.name(), 7L);
        BdlPlay finalPlay = play(8L, 10L);
        Play latestPlay = persistedPlay(8L, 10L, false, false, false);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(Game.STATUS_FINAL)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(new PollerGameWriter.GameUpsertResult(
                        finalGame,
                        GameLifecycle.LIVE.name(),
                        GameLifecycle.FINAL.name(),
                        true
                ));
        when(balldontlieClient.getPlays(100L, 7L))
                .thenReturn(new ListResponse<>(List.of(finalPlay), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of(plateAppearance(1L, 10L))));
        when(gameWriter.appendPlay(finalGame, finalPlay, now)).thenAnswer(invocation -> {
            finalGame.setLastPlayOrder(8L);
            return true;
        });
        when(gameWriter.updateRunnerStates(eq(100L), any()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                100L,
                org.springframework.data.domain.PageRequest.of(0, 1)
        )).thenReturn(List.of(latestPlay));

        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher, times(2)).publish(taskCaptor.capture());
        assertThat(taskCaptor.getAllValues()).extracting(ScoreTask::lifecycleState)
                .containsExactly(GameLifecycle.LIVE.name(), GameLifecycle.FINAL.name());
        assertThat(taskCaptor.getAllValues()).extracting(ScoreTask::lastPlayOrder)
                .containsExactly(8L, 8L);
        assertThat(taskCaptor.getAllValues()).extracting(ScoreTask::observedAt)
                .containsExactly(now, now.plusMillis(1));
        InOrder inOrder = inOrder(gameWriter, scoreTaskPublisher);
        inOrder.verify(gameWriter).appendPlay(finalGame, finalPlay, now);
        inOrder.verify(scoreTaskPublisher).publish(taskCaptor.getAllValues().get(0));
        inOrder.verify(scoreTaskPublisher).publish(taskCaptor.getAllValues().get(1));
    }

    @Test
    void poll_shouldPublishTerminalTaskWhenScheduledGameTransitionsDirectlyToFinal() {
        Game finalGame = game(GameLifecycle.FINAL.name(), null);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(Game.STATUS_FINAL)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(new PollerGameWriter.GameUpsertResult(
                        finalGame,
                        GameLifecycle.SCHEDULED.name(),
                        GameLifecycle.FINAL.name(),
                        false
                ));
        when(balldontlieClient.getPlays(100L, null))
                .thenReturn(new ListResponse<>(List.of(), new ListResponse.Meta(null, 100)));

        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        verify(balldontlieClient, never()).getPlateAppearancesRaw(100L);
        assertThat(taskCaptor.getValue().lifecycleState()).isEqualTo(GameLifecycle.FINAL.name());
    }

    @Test
    void poll_shouldContinueWithNextGameWhenOneGameFails() {
        Game firstGame = game(100L, GameLifecycle.LIVE.name(), 1L);
        Game secondGame = game(200L, GameLifecycle.LIVE.name(), 1L);
        BdlPlay firstPlay = play(1L, 10L);
        BdlPlay secondPlay = play(1L, 20L);
        Play latestPlay = persistedPlay(200L, 1L, 20L, true, false, false);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(100L, Game.STATUS_IN_PROGRESS), gameDto(200L, Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(liveResult(firstGame), liveResult(secondGame));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenReturn(new ListResponse<>(List.of(firstPlay), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of()));
        when(gameWriter.appendPlay(firstGame, firstPlay, now)).thenThrow(new RuntimeException("저장 실패"));
        when(balldontlieClient.getPlays(200L, 1L))
                .thenReturn(new ListResponse<>(List.of(secondPlay), new ListResponse.Meta(null, 100)));
        when(gameWriter.appendPlay(secondGame, secondPlay, now)).thenReturn(true);
        when(balldontlieClient.getPlateAppearancesRaw(200L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of()));
        when(gameWriter.updateRunnerStates(200L, List.of()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(200L, org.springframework.data.domain.PageRequest.of(0, 1)))
                .thenReturn(List.of(latestPlay));

        poller.poll();

        verify(gameWriter).appendPlay(secondGame, secondPlay, now);
        verify(scoreTaskPublisher).publish(any(ScoreTask.class));
    }

    @Test
    void poll_shouldStopRemainingGamesAndRecordBackoffWhenRateLimited() {
        Game firstGame = game(100L, GameLifecycle.LIVE.name(), 1L);
        Game secondGame = game(200L, GameLifecycle.LIVE.name(), 1L);
        when(balldontlieClient.getGames(now.atZone(ZoneOffset.UTC).toLocalDate()))
                .thenReturn(List.of(gameDto(100L, Game.STATUS_IN_PROGRESS), gameDto(200L, Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now)))
                .thenReturn(liveResult(firstGame), liveResult(secondGame));
        when(gameRepository.findByLifecycleState(GameLifecycle.LIVE.name()))
                .thenReturn(List.of(firstGame, secondGame));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenThrow(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS));

        poller.poll();
        poller.poll();

        verify(balldontlieClient, times(1)).getPlays(100L, 1L);
        verify(balldontlieClient, never()).getPlays(200L, 1L);
    }

    @Test
    void poll_shouldPublishHeartbeatAfterIntervalWhenNoNewPlay() {
        MutableClock clock = new MutableClock(now);
        OperationalPoller heartbeatPoller = poller(clock, 0);
        Game liveGame = game(GameLifecycle.LIVE.name(), 1L);
        BdlPlay fetchedPlay = play(2L, 10L);
        Play latestPlay = persistedPlay(2L, 10L, false, true, false);
        when(balldontlieClient.getGames(any(LocalDate.class)))
                .thenReturn(List.of(gameDto(Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), any(Instant.class)))
                .thenReturn(liveResult(liveGame));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenReturn(new ListResponse<>(List.of(fetchedPlay), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlays(100L, 2L))
                .thenReturn(new ListResponse<>(List.of(), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of()));
        when(gameWriter.appendPlay(liveGame, fetchedPlay, now)).thenAnswer(invocation -> {
            liveGame.setLastPlayOrder(2L);
            return true;
        });
        when(gameWriter.updateRunnerStates(100L, List.of()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                100L,
                org.springframework.data.domain.PageRequest.of(0, 1)
        )).thenReturn(List.of(latestPlay));

        heartbeatPoller.poll();
        clearInvocations(scoreTaskPublisher, balldontlieClient);
        clock.advance(Duration.ofSeconds(75));

        heartbeatPoller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher).publish(taskCaptor.capture());
        verify(balldontlieClient, never()).getPlateAppearancesRaw(100L);
        assertThat(taskCaptor.getValue().observedAt()).isEqualTo(now.plusSeconds(75));
        assertThat(taskCaptor.getValue().lastPlayOrder()).isEqualTo(2L);
        assertThat(taskCaptor.getValue().plateAppearances()).isEmpty();
    }

    @Test
    void poll_shouldNotPublishHeartbeatBeforeInterval() {
        MutableClock clock = new MutableClock(now);
        OperationalPoller heartbeatPoller = poller(clock, 0);
        Game liveGame = game(GameLifecycle.LIVE.name(), 1L);
        BdlPlay fetchedPlay = play(2L, 10L);
        Play latestPlay = persistedPlay(2L, 10L, false, true, false);
        when(balldontlieClient.getGames(any(LocalDate.class)))
                .thenReturn(List.of(gameDto(Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), any(Instant.class)))
                .thenReturn(liveResult(liveGame));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenReturn(new ListResponse<>(List.of(fetchedPlay), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlays(100L, 2L))
                .thenReturn(new ListResponse<>(List.of(), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of()));
        when(gameWriter.appendPlay(liveGame, fetchedPlay, now)).thenAnswer(invocation -> {
            liveGame.setLastPlayOrder(2L);
            return true;
        });
        when(gameWriter.updateRunnerStates(100L, List.of()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                100L,
                org.springframework.data.domain.PageRequest.of(0, 1)
        )).thenReturn(List.of(latestPlay));

        heartbeatPoller.poll();
        clearInvocations(scoreTaskPublisher, balldontlieClient);
        clock.advance(Duration.ofSeconds(74));

        heartbeatPoller.poll();

        verify(scoreTaskPublisher, never()).publish(any(ScoreTask.class));
        verify(balldontlieClient, never()).getPlateAppearancesRaw(100L);
    }

    @Test
    void poll_shouldPublishOnlyRegularTaskWhenNewPlayExists() {
        Game liveGame = game(GameLifecycle.LIVE.name(), 1L);
        BdlPlay fetchedPlay = play(2L, 10L);
        Play latestPlay = persistedPlay(2L, 10L, false, false, false);
        when(balldontlieClient.getGames(any(LocalDate.class)))
                .thenReturn(List.of(gameDto(Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now))).thenReturn(liveResult(liveGame));
        when(balldontlieClient.getPlays(100L, 1L))
                .thenReturn(new ListResponse<>(List.of(fetchedPlay), new ListResponse.Meta(null, 100)));
        when(balldontlieClient.getPlateAppearancesRaw(100L))
                .thenReturn(new BdlDtos.PlateAppearancesRaw(null, List.of()));
        when(gameWriter.appendPlay(liveGame, fetchedPlay, now)).thenReturn(true);
        when(gameWriter.updateRunnerStates(100L, List.of()))
                .thenReturn(new PollerRunnerStateMatcher.MatchResult(List.of(), 0, 0));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                100L,
                org.springframework.data.domain.PageRequest.of(0, 1)
        )).thenReturn(List.of(latestPlay));

        poller.poll();

        verify(scoreTaskPublisher, times(1)).publish(any(ScoreTask.class));
    }

    @Test
    void poll_shouldSkipHeartbeatWhenStoredPlayDoesNotExist() {
        Game liveGame = game(GameLifecycle.LIVE.name(), null);
        when(balldontlieClient.getGames(any(LocalDate.class)))
                .thenReturn(List.of(gameDto(Game.STATUS_IN_PROGRESS)));
        when(gameWriter.upsertGame(any(BdlGame.class), eq(now))).thenReturn(liveResult(liveGame));
        when(balldontlieClient.getPlays(100L, null))
                .thenReturn(new ListResponse<>(List.of(), new ListResponse.Meta(null, 100)));
        when(playRepository.findByGameIdOrderByPlayOrderDesc(
                100L,
                org.springframework.data.domain.PageRequest.of(0, 1)
        )).thenReturn(List.of());

        poller.poll();

        verify(scoreTaskPublisher, never()).publish(any(ScoreTask.class));
        verify(balldontlieClient, never()).getPlateAppearancesRaw(100L);
    }

    @Test
    void poll_shouldRequestTodayAndNextTwoDaysWhenLookaheadIsTwoDays() {
        OperationalPoller twoDayLookaheadPoller = poller(now, 2);
        when(balldontlieClient.getGames(any(LocalDate.class))).thenReturn(List.of());

        twoDayLookaheadPoller.poll();

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        verify(balldontlieClient).getGames(today);
        verify(balldontlieClient).getGames(today.plusDays(1));
        verify(balldontlieClient).getGames(today.plusDays(2));
        verify(balldontlieClient, times(3)).getGames(any(LocalDate.class));
    }

    @Test
    void poll_shouldIncludeGameDateAtThirtySixHourBoundaryWithTwoDayLookahead() {
        Instant afternoonNow = Instant.parse("2026-07-08T13:00:00Z");
        Instant gameStartAtBoundary = afternoonNow.plus(Duration.ofHours(36));
        LocalDate gameDate = LocalDate.ofInstant(gameStartAtBoundary, ZoneOffset.UTC);
        OperationalPoller twoDayLookaheadPoller = poller(afternoonNow, 2);
        when(balldontlieClient.getGames(any(LocalDate.class))).thenReturn(List.of());

        twoDayLookaheadPoller.poll();

        verify(balldontlieClient).getGames(gameDate);
    }

    @Test
    void properties_shouldUseTwoDayLookaheadWhenConfiguredValueIsNegative() {
        assertThat(properties(-1).slateLookaheadDays()).isEqualTo(2);
    }

    private OperationalPoller poller(Instant fixedNow, int slateLookaheadDays) {
        Clock clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        return poller(clock, slateLookaheadDays);
    }

    private OperationalPoller poller(Clock clock, int slateLookaheadDays) {
        return new OperationalPoller(
                balldontlieClient,
                gameRepository,
                gameWriter,
                liveGameCycleWriter,
                new ScoreTaskFactory(),
                scoreTaskPublisher,
                notificationEventPublisher,
                properties(slateLookaheadDays),
                new PollerRateLimiter(1000, clock),
                paRawArchiveUploader,
                clock
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static PollerProperties properties() {
        return properties(0);
    }

    private static PollerProperties properties(int slateLookaheadDays) {
        return new PollerProperties(
                true,
                Duration.ofSeconds(20),
                Duration.ofSeconds(75),
                Duration.ofMinutes(10),
                0,
                slateLookaheadDays,
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

    private static Game game(String lifecycle, Long lastPlayOrder) {
        return game(100L, lifecycle, lastPlayOrder);
    }

    private static Game game(long gameId, String lifecycle, Long lastPlayOrder) {
        Game game = new Game();
        game.setId(gameId);
        game.setLifecycleState(lifecycle);
        game.setLastPlayOrder(lastPlayOrder);
        return game;
    }

    private static Play persistedPlay(Long order, Long batterId, boolean first, boolean second, boolean third) {
        return persistedPlay(100L, order, batterId, first, second, third);
    }

    private static Play persistedPlay(
            long gameId,
            Long order,
            Long batterId,
            boolean first,
            boolean second,
            boolean third
    ) {
        Play play = new Play();
        play.setGameId(gameId);
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
        return gameDto(100L, status);
    }

    private static BdlGame gameDto(long gameId, String status) {
        return new BdlGame(gameId, "2026-07-08T00:00:00Z", status, 1,
                null, null, null, null, null, null, null);
    }

    private static PollerGameWriter.GameUpsertResult liveResult(Game game) {
        return new PollerGameWriter.GameUpsertResult(
                game,
                GameLifecycle.LIVE.name(),
                GameLifecycle.LIVE.name(),
                false
        );
    }

    private static BdlPlay play(Long order, Long batterId) {
        return new BdlPlay(order, "at_bat", 1, "Top", "play", 0, 0, false, 0, 1, 2, 1, batterId, 99L);
    }

    private static BdlPlateAppearance plateAppearance(Long number, Long batterId) {
        return new BdlPlateAppearance(number, 100L, 1, "top", batterId, 99L, true, true, false);
    }
}
