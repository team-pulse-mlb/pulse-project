package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTaskPublisher;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PregamePollerTest {

    private final BalldontlieClient balldontlieClient = mock(BalldontlieClient.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LineupRepository lineupRepository = mock(LineupRepository.class);
    private final PregameGameWriter pregameWriter = mock(PregameGameWriter.class);
    private final ScoreTaskPublisher scoreTaskPublisher = mock(ScoreTaskPublisher.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-08T00:00:00Z"));
    private final PregamePoller poller = new PregamePoller(
            balldontlieClient,
            gameRepository,
            lineupRepository,
            pregameWriter,
            new ScoreTaskFactory(),
            scoreTaskPublisher,
            properties(),
            new PollerRateLimiter(1000, clock),
            clock
    );

    @Test
    void poll_shouldPublishPregameTaskOnceOnPregameNearEntry() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));

        poller.poll();
        clock.advance(Duration.ofMinutes(1));
        poller.poll();

        ArgumentCaptor<ScoreTask> taskCaptor = ArgumentCaptor.forClass(ScoreTask.class);
        verify(scoreTaskPublisher, times(1)).publish(taskCaptor.capture());
        assertThat(taskCaptor.getValue().lifecycleState()).isEqualTo(ScoreTaskFactory.PREGAME_LIFECYCLE);
        assertThat(taskCaptor.getValue().situation()).isNull();
    }

    @Test
    void poll_shouldHonorLineupsIntervalPerLifecycleState() {
        Game farGame = game(100L, GameLifecycle.PREGAME_FAR.name());
        Game nearGame = game(101L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(farGame, nearGame));

        poller.poll();
        clock.advance(Duration.ofMinutes(16));
        poller.poll();

        verify(balldontlieClient).getLineups(List.of(100L, 101L));
        verify(balldontlieClient).getLineups(List.of(101L));
    }

    @Test
    void poll_shouldPollOddsOnlyWhenPregameNearGameExists() {
        Game farGame = game(100L, GameLifecycle.PREGAME_FAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(farGame));

        poller.poll();

        verify(balldontlieClient, never()).getOdds(any());
    }

    @Test
    void poll_shouldPollOddsByPregameNearGameIds() {
        Game nearGame = game(101L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));

        poller.poll();

        verify(balldontlieClient).getOdds(List.of(101L));
    }

    @Test
    void poll_shouldRunStandingsBatchOncePerDayAndTriggerPregameTasks() {
        clock.set(Instant.parse("2026-07-08T10:30:00Z"));
        Game farGame = game(100L, GameLifecycle.PREGAME_FAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(farGame));
        when(pregameWriter.upsertStandings(eq(2026), any(), anyList(), any())).thenReturn(true);

        poller.poll();
        clock.advance(Duration.ofMinutes(1));
        poller.poll();

        verify(balldontlieClient, times(1)).getStandings(2026);
        verify(scoreTaskPublisher, times(1)).publish(any(ScoreTask.class));
    }

    @Test
    void poll_shouldPublishTaskEvenWhenSeasonStatsRefreshFails() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L))
                .thenReturn(List.of(probablePitcher(100L, 7L)));
        when(balldontlieClient.getPlayerSeasonStats(anyInt(), anyList()))
                .thenThrow(new IllegalStateException("season stats down"));

        poller.poll();

        verify(scoreTaskPublisher).publish(any(ScoreTask.class));
        verify(pregameWriter, never()).upsertPlayerSeasonStats(anyInt(), anyList(), any());
    }

    @Test
    void poll_shouldLoadSeasonStatsForProbablePitchersBeforePublish() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L))
                .thenReturn(List.of(probablePitcher(100L, 7L), probablePitcher(100L, 8L)));

        poller.poll();

        verify(balldontlieClient).getPlayerSeasonStats(2026, List.of(7L, 8L));
        verify(pregameWriter).upsertPlayerSeasonStats(eq(2026), anyList(), any());
        verify(scoreTaskPublisher).publish(any(ScoreTask.class));
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

    private static Game game(long id, String lifecycleState) {
        Game game = new Game();
        game.setId(id);
        game.setStatus(Game.STATUS_SCHEDULED);
        game.setLifecycleState(lifecycleState);
        game.setStartTime(Instant.parse("2026-07-08T23:00:00Z"));
        return game;
    }

    private static Lineup probablePitcher(long gameId, long playerId) {
        Lineup lineup = new Lineup();
        lineup.setId(playerId * 10);
        lineup.setGameId(gameId);
        lineup.setPlayerId(playerId);
        lineup.setTeamId(1L);
        lineup.setIsProbablePitcher(true);
        return lineup;
    }

    private static final class MutableClock extends Clock {

        private Instant current;

        private MutableClock(Instant initial) {
            this.current = initial;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        private void set(Instant instant) {
            current = instant;
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
            return current;
        }
    }
}
