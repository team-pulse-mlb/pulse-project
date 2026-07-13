package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.message.ScoreTask;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Lineup;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.OddsSnapshot;
import com.pulse.domain.OddsSnapshotRepository;
import com.pulse.domain.PlayerSeasonStat;
import com.pulse.domain.PlayerSeasonStatId;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.Standing;
import com.pulse.domain.StandingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PregameScoringServiceTest {

    private final GameRepository gameRepository = mock(GameRepository.class);
    private final LineupRepository lineupRepository = mock(LineupRepository.class);
    private final OddsSnapshotRepository oddsSnapshotRepository = mock(OddsSnapshotRepository.class);
    private final StandingRepository standingRepository = mock(StandingRepository.class);
    private final PlayerSeasonStatRepository playerSeasonStatRepository = mock(PlayerSeasonStatRepository.class);
    private final PregameScoringService service = new PregameScoringService(
            gameRepository,
            lineupRepository,
            oddsSnapshotRepository,
            standingRepository,
            playerSeasonStatRepository,
            TestScoringProperties.version5()
    );

    private final Instant observedAt = Instant.parse("2026-07-08T01:00:00Z");

    @Test
    void handle_shouldCalculatePregameScoreFromOddsPitchersAndStandings() {
        Game game = game();
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(oddsSnapshotRepository.findByGameIdAndSnapshotType(100L, OddsSnapshot.SNAPSHOT_PREGAME_FINAL))
                .thenReturn(List.of(odds(-110, -110)));
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L))
                .thenReturn(List.of(pitcher(7L), pitcher(8L)));
        when(playerSeasonStatRepository.findById(new PlayerSeasonStatId(2026, 7L)))
                .thenReturn(Optional.of(stat("2.60")));
        when(playerSeasonStatRepository.findById(new PlayerSeasonStatId(2026, 8L)))
                .thenReturn(Optional.of(stat("2.60")));
        when(standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(1L))
                .thenReturn(Optional.of(standing("0.560", "50.00")));
        when(standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(2L))
                .thenReturn(Optional.of(standing("0.555", "45.00")));

        service.handle(task());

        assertThat(game.getPregameScore()).isEqualTo(100);
        assertThat(component(game, "closeness").get("source")).isEqualTo("ODDS_PREGAME_FINAL");
        assertThat(component(game, "starterMatchup").get("playerIds")).isEqualTo(List.of(7L, 8L));
        verify(gameRepository).save(game);
    }

    @Test
    void handle_shouldFallbackToWinPercentWhenOddsAreMissing() {
        Game game = game();
        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));
        when(oddsSnapshotRepository.findByGameIdAndSnapshotType(100L, OddsSnapshot.SNAPSHOT_PREGAME_FINAL))
                .thenReturn(List.of());
        when(oddsSnapshotRepository.findByGameIdAndSnapshotType(100L, OddsSnapshot.SNAPSHOT_FIRST_SEEN))
                .thenReturn(List.of());
        when(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L)).thenReturn(List.of());
        when(standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(1L))
                .thenReturn(Optional.of(standing("0.600", null)));
        when(standingRepository.findTopByTeamIdOrderBySnapshotDateDesc(2L))
                .thenReturn(Optional.of(standing("0.580", null)));

        service.handle(task());

        assertThat(game.getPregameScore()).isEqualTo(26);
        assertThat(component(game, "closeness").get("source")).isEqualTo("WIN_PERCENT");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> component(Game game, String name) {
        Map<String, Object> components = (Map<String, Object>) game.getPregameInputs().get("components");
        return (Map<String, Object>) components.get(name);
    }

    private ScoreTask task() {
        return new ScoreTask(100L, observedAt, null, "PREGAME", null);
    }

    private static Game game() {
        Game game = new Game();
        game.setId(100L);
        game.setStatus(Game.STATUS_SCHEDULED);
        game.setStartTime(Instant.parse("2026-07-08T23:00:00Z"));
        game.setHomeTeamId(1L);
        game.setAwayTeamId(2L);
        return game;
    }

    private static OddsSnapshot odds(int home, int away) {
        OddsSnapshot snapshot = new OddsSnapshot();
        snapshot.setGameId(100L);
        snapshot.setSnapshotType(OddsSnapshot.SNAPSHOT_PREGAME_FINAL);
        snapshot.setMoneylineHomeOdds(home);
        snapshot.setMoneylineAwayOdds(away);
        return snapshot;
    }

    private static Lineup pitcher(long playerId) {
        Lineup lineup = new Lineup();
        lineup.setGameId(100L);
        lineup.setPlayerId(playerId);
        lineup.setIsProbablePitcher(true);
        return lineup;
    }

    private static PlayerSeasonStat stat(String era) {
        PlayerSeasonStat stat = new PlayerSeasonStat();
        stat.setSeason(2026);
        stat.setPlayerId(7L);
        stat.setPitchingEra(new BigDecimal(era));
        return stat;
    }

    private static Standing standing(String winPercent, String playoffPercent) {
        Standing standing = new Standing();
        standing.setWinPercent(new BigDecimal(winPercent));
        if (playoffPercent != null) {
            standing.setPlayoffPercent(new BigDecimal(playoffPercent));
        }
        return standing;
    }
}
