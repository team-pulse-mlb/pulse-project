package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BalldontlieClient;
import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.domain.Game;
import com.pulse.domain.GameLifecycle;
import com.pulse.domain.GameRepository;
import com.pulse.poller.PregameTransitionWriter.PregameWriteOutcome;
import com.pulse.poller.PregameTransitionWriter.PregameWriteRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class PregamePollerTest {

    private final BalldontlieClient balldontlieClient = mock(BalldontlieClient.class);
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PregameTransitionWriter transitionWriter = mock(PregameTransitionWriter.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-08T00:00:00Z"));
    private PregamePoller poller;

    @BeforeEach
    void setUp() {
        when(balldontlieClient.getLineups(anyList())).thenReturn(List.of());
        when(balldontlieClient.getOdds(anyList())).thenReturn(List.of());
        when(balldontlieClient.getStandings(anyInt())).thenReturn(List.of());
        when(balldontlieClient.getPlayerSeasonStats(anyInt(), anyList())).thenReturn(List.of());
        when(transitionWriter.write(any())).thenAnswer(invocation -> {
            PregameWriteRequest request = invocation.getArgument(0);
            Set<Long> publishedGameIds = new LinkedHashSet<>(request.triggeredGameIds());
            if (request.standingsBatch() != null) {
                publishedGameIds.addAll(request.gamesById().keySet());
            }
            return new PregameWriteOutcome(Set.copyOf(publishedGameIds), Set.copyOf(publishedGameIds));
        });
        poller = new PregamePoller(
                balldontlieClient,
                gameRepository,
                transitionWriter,
                properties(),
                new PollerRateLimiter(1000, clock),
                clock
        );
    }

    @Test
    void poll_shouldPublishPregameTaskOnceOnPregameNearEntry() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));

        poller.poll();
        clock.advance(Duration.ofMinutes(1));
        poller.poll();

        ArgumentCaptor<PregameWriteRequest> requestCaptor = ArgumentCaptor.forClass(PregameWriteRequest.class);
        verify(transitionWriter, times(2)).write(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).triggeredGameIds()).containsExactly(100L);
        assertThat(requestCaptor.getAllValues().get(1).triggeredGameIds()).isEmpty();
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

        poller.poll();
        clock.advance(Duration.ofMinutes(1));
        poller.poll();

        verify(balldontlieClient, times(1)).getStandings(2026);
        ArgumentCaptor<PregameWriteRequest> requestCaptor = ArgumentCaptor.forClass(PregameWriteRequest.class);
        verify(transitionWriter, times(2)).write(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues().get(0).standingsBatch()).isNotNull();
        assertThat(requestCaptor.getAllValues().get(1).standingsBatch()).isNull();
    }

    @Test
    void poll_shouldPublishTaskEvenWhenSeasonStatsRefreshFails() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));
        when(balldontlieClient.getLineups(anyList())).thenReturn(List.of(probablePitcher(100L, 7L)));
        when(balldontlieClient.getPlayerSeasonStats(anyInt(), anyList()))
                .thenThrow(new IllegalStateException("season stats down"));

        poller.poll();

        ArgumentCaptor<PregameWriteRequest> requestCaptor = ArgumentCaptor.forClass(PregameWriteRequest.class);
        verify(transitionWriter).write(requestCaptor.capture());
        assertThat(requestCaptor.getValue().playerSeasonStats()).isEmpty();
        assertThat(requestCaptor.getValue().triggeredGameIds()).containsExactly(100L);
    }

    @Test
    void poll_shouldLoadSeasonStatsFromFetchedLineupsBeforeTransactionalWrite() {
        Game nearGame = game(100L, GameLifecycle.PREGAME_NEAR.name());
        BdlPlayerSeasonStat stat = seasonStat(7L);
        when(gameRepository.findByLifecycleStateIn(anyList())).thenReturn(List.of(nearGame));
        when(balldontlieClient.getLineups(anyList()))
                .thenReturn(List.of(probablePitcher(100L, 7L), probablePitcher(100L, 8L)));
        when(balldontlieClient.getPlayerSeasonStats(2026, List.of(7L, 8L))).thenReturn(List.of(stat));

        poller.poll();

        InOrder order = inOrder(balldontlieClient, transitionWriter);
        order.verify(balldontlieClient).getLineups(List.of(100L));
        order.verify(balldontlieClient).getPlayerSeasonStats(2026, List.of(7L, 8L));
        order.verify(balldontlieClient).getOdds(List.of(100L));
        order.verify(transitionWriter).write(any(PregameWriteRequest.class));

        ArgumentCaptor<PregameWriteRequest> requestCaptor = ArgumentCaptor.forClass(PregameWriteRequest.class);
        verify(transitionWriter).write(requestCaptor.capture());
        assertThat(requestCaptor.getValue().playerSeasonStats()).containsExactly(stat);
        assertThat(requestCaptor.getValue().probablePitcherIdsByGameId().get(100L))
                .containsExactly(7L, 8L);
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

    private static BdlLineup probablePitcher(long gameId, long playerId) {
        return new BdlLineup(
                playerId * 10,
                gameId,
                new BdlLineup.Player(playerId, "Pitcher", "Pitcher", null, "SP"),
                new BdlLineup.TeamRef(1L),
                null,
                "SP",
                true
        );
    }

    private static BdlPlayerSeasonStat seasonStat(long playerId) {
        return new BdlPlayerSeasonStat(
                playerId,
                null,
                2026,
                new BigDecimal("3.10"),
                null,
                null,
                null,
                null,
                null,
                null
        );
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
