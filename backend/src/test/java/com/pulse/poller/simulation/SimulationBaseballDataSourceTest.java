package com.pulse.poller.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SimulationBaseballDataSourceTest {
    private final GameRepository gameRepository = mock(GameRepository.class);
    private final PlayRepository playRepository = mock(PlayRepository.class);
    private final Instant now = Instant.parse("2026-07-12T12:00:00Z");

    @Test
    void 배속과_시작_오프셋에_따라_공개할_플레이를_결정한다() {
        SimulationBaseballDataSource source = source(new SimulationProperties(
                true, 10L, 9_000_000_010L, List.of(), 5.0, Duration.ofSeconds(40),
                Duration.ofSeconds(20), "START", null, null, 1000));

        assertThat(source.getPlays(9_000_000_010L, null).data())
                .extracting(play -> play.order())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void 커서_이후의_플레이만_반환한다() {
        SimulationBaseballDataSource source = source(new SimulationProperties(
                true, 10L, 9_000_000_010L, List.of(), 1.0, Duration.ofSeconds(40),
                Duration.ofSeconds(20), "START", null, null, 1000));

        assertThat(source.getPlays(9_000_000_010L, 2L).data())
                .extracting(play -> play.order())
                .containsExactly(3L);
    }

    @Test
    void 원본과_대상_경기_ID가_같으면_중단한다() {
        SimulationProperties properties = new SimulationProperties(
                true, 10L, 10L, List.of(), 1.0, Duration.ZERO,
                Duration.ofSeconds(20), "START", null, null, 1000);
        SimulationBaseballDataSource source = new SimulationBaseballDataSource(
                properties, gameRepository, playRepository, Clock.fixed(now, ZoneOffset.UTC));

        assertThatThrownBy(() -> source.getPlays(10L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    @Test
    void 여러_경기의_상태를_함께_반환하고_경기_ID별로_플레이를_조회한다() {
        SimulationProperties properties = new SimulationProperties(
                true, null, null,
                List.of(
                        new SimulationProperties.GameSpec(
                                10L, 9_000_000_010L, Duration.ofSeconds(40), "START"),
                        new SimulationProperties.GameSpec(
                                20L, 9_000_000_020L, Duration.ofSeconds(20), "START"),
                        new SimulationProperties.GameSpec(
                                30L, 9_000_000_030L, Duration.ofHours(-3), "START")),
                1.0, Duration.ZERO, Duration.ofSeconds(20), "START", null, null, 1000);
        SimulationBaseballDataSource source = source(properties);

        List<BdlGame> games = source.getGames(LocalDate.of(2026, 7, 12));

        assertThat(games)
                .extracting(BdlGame::id, BdlGame::status)
                .containsExactly(
                        tuple(9_000_000_010L, Game.STATUS_FINAL),
                        tuple(9_000_000_020L, Game.STATUS_IN_PROGRESS),
                        tuple(9_000_000_030L, Game.STATUS_SCHEDULED));
        BdlGame scheduled = games.get(2);
        assertThat(scheduled.date()).isEqualTo(now.plus(Duration.ofHours(3)).toString());
        assertThat(scheduled.period()).isEqualTo(1);
        assertThat(scheduled.homeTeamData().runs()).isZero();
        assertThat(scheduled.awayTeamData().runs()).isZero();

        assertThat(source.getPlays(9_000_000_020L, null).data())
                .extracting(play -> play.order())
                .containsExactly(101L, 102L);
        assertThat(source.getPlays(9_000_000_030L, null).data()).isEmpty();
        assertThat(source.getPlays(99L, null).data()).isEmpty();
    }

    @Test
    void 종료_직전_프리셋은_처음에_진행_상태로_노출한다() {
        SimulationProperties properties = new SimulationProperties(
                true, null, null,
                List.of(new SimulationProperties.GameSpec(
                        10L, 9_000_000_010L, Duration.ZERO, "FINISH")),
                1.0, Duration.ZERO, Duration.ofSeconds(20), "START", null, null, 1000);
        SimulationBaseballDataSource source = source(properties);

        List<BdlGame> games = source.getGames(LocalDate.of(2026, 7, 12));

        assertThat(games).singleElement()
                .extracting(BdlGame::status)
                .isEqualTo(Game.STATUS_IN_PROGRESS);
    }

    private SimulationBaseballDataSource source(SimulationProperties properties) {
        for (SimulationProperties.ResolvedGameSpec spec : properties.resolvedGames()) {
            when(gameRepository.findById(spec.sourceGameId())).thenReturn(Optional.of(game(spec.sourceGameId())));
            when(gameRepository.existsById(spec.targetGameId())).thenReturn(false);
            when(playRepository.findByGameIdOrderByPlayOrderAsc(spec.sourceGameId()))
                    .thenReturn(plays(spec.sourceGameId()));
        }
        return new SimulationBaseballDataSource(
                properties, gameRepository, playRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Game game(long gameId) {
        Game game = new Game();
        game.setId(gameId);
        game.setStatus(Game.STATUS_FINAL);
        game.setHomeTeamId(gameId * 10 + 1);
        game.setAwayTeamId(gameId * 10 + 2);
        return game;
    }

    private static List<Play> plays(long gameId) {
        long firstOrder = (gameId - 10L) * 10L + 1L;
        return List.of(play(firstOrder, 1), play(firstOrder + 1, 1), play(firstOrder + 2, 7));
    }

    private static Play play(long order, int inning) {
        Play play = new Play();
        play.setPlayOrder(order);
        play.setInning(inning);
        play.setHomeScore(0);
        play.setAwayScore(0);
        play.setScoringPlay(order % 10 == 3L);
        return play;
    }
}
