package com.pulse.poller.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
                true, 10L, 9_000_000_010L, 5.0, Duration.ofSeconds(40), Duration.ofSeconds(20), "START", null, null, 1000));

        assertThat(source.getPlays(9_000_000_010L, null).data())
                .extracting(play -> play.order())
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void 커서_이후의_플레이만_반환한다() {
        SimulationBaseballDataSource source = source(new SimulationProperties(
                true, 10L, 9_000_000_010L, 1.0, Duration.ofSeconds(40), Duration.ofSeconds(20), "START", null, null, 1000));

        assertThat(source.getPlays(9_000_000_010L, 2L).data())
                .extracting(play -> play.order())
                .containsExactly(3L);
    }

    @Test
    void 원본과_대상_경기_ID가_같으면_중단한다() {
        SimulationBaseballDataSource source = source(new SimulationProperties(
                true, 10L, 10L, 1.0, Duration.ZERO, Duration.ofSeconds(20), "START", null, null, 1000));

        assertThatThrownBy(() -> source.getPlays(10L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must differ");
    }

    private SimulationBaseballDataSource source(SimulationProperties properties) {
        Game game = new Game();
        game.setId(10L);
        game.setStatus(Game.STATUS_FINAL);
        game.setHomeTeamId(1L);
        game.setAwayTeamId(2L);
        when(gameRepository.findById(10L)).thenReturn(Optional.of(game));
        when(gameRepository.existsById(properties.resolvedTargetGameId())).thenReturn(false);
        when(playRepository.findByGameIdOrderByPlayOrderAsc(10L)).thenReturn(List.of(play(1L, 1), play(2L, 1), play(3L, 7)));
        return new SimulationBaseballDataSource(properties, gameRepository, playRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Play play(long order, int inning) {
        Play play = new Play();
        play.setPlayOrder(order);
        play.setInning(inning);
        play.setHomeScore(0);
        play.setAwayScore(0);
        play.setScoringPlay(order == 3L);
        return play;
    }
}
