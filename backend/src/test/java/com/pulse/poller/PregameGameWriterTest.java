package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.client.BdlDtos.BdlLineup;
import com.pulse.common.client.BdlDtos.BdlOdds;
import com.pulse.common.client.BdlDtos.BdlPlayerSeasonStat;
import com.pulse.common.client.BdlDtos.BdlStanding;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.LineupRepository;
import com.pulse.domain.OddsSnapshot;
import com.pulse.domain.OddsSnapshotRepository;
import com.pulse.domain.PlayerRepository;
import com.pulse.domain.PlayerSeasonStatId;
import com.pulse.domain.PlayerSeasonStatRepository;
import com.pulse.domain.StandingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import(PregameGameWriter.class)
@TestPropertySource(properties = "spring.flyway.enabled=false")
class PregameGameWriterTest {

    @Autowired
    private PregameGameWriter writer;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private LineupRepository lineupRepository;

    @Autowired
    private OddsSnapshotRepository oddsSnapshotRepository;

    @Autowired
    private StandingRepository standingRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PlayerSeasonStatRepository playerSeasonStatRepository;

    private final Instant observedAt = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void upsertLineups_shouldDetectProbablePitcherChangeOnly() {
        Set<Long> firstPass = writer.upsertLineups(List.of(lineup(1L, 100L, 7L, false)), observedAt);
        Set<Long> samePass = writer.upsertLineups(List.of(lineup(1L, 100L, 7L, false)), observedAt.plusSeconds(60));
        Set<Long> changedPass = writer.upsertLineups(List.of(lineup(1L, 100L, 7L, true)), observedAt.plusSeconds(120));

        assertThat(firstPass).containsExactly(100L);
        assertThat(samePass).isEmpty();
        assertThat(changedPass).containsExactly(100L);
        assertThat(lineupRepository.findByGameIdAndIsProbablePitcherTrue(100L)).hasSize(1);
        assertThat(playerRepository.findById(7L)).isPresent();
    }

    @Test
    void upsertOdds_shouldKeepFirstSeenAndOverwritePregameFinal() {
        Map<Long, Instant> startTimes = Map.of(100L, observedAt.plusSeconds(3600));

        Set<Long> first = writer.upsertOdds(List.of(odds(100L, -120)), startTimes, observedAt);
        Set<Long> unchanged = writer.upsertOdds(List.of(odds(100L, -120)), startTimes, observedAt.plusSeconds(60));
        Set<Long> moved = writer.upsertOdds(List.of(odds(100L, -140)), startTimes, observedAt.plusSeconds(120));

        OddsSnapshot firstSeen = oddsSnapshotRepository
                .findByGameIdAndVendorAndSnapshotType(100L, "book-a", OddsSnapshot.SNAPSHOT_FIRST_SEEN)
                .orElseThrow();
        OddsSnapshot pregameFinal = oddsSnapshotRepository
                .findByGameIdAndVendorAndSnapshotType(100L, "book-a", OddsSnapshot.SNAPSHOT_PREGAME_FINAL)
                .orElseThrow();
        assertThat(first).containsExactly(100L);
        assertThat(unchanged).isEmpty();
        assertThat(moved).containsExactly(100L);
        assertThat(firstSeen.getMoneylineHomeOdds()).isEqualTo(-120);
        assertThat(pregameFinal.getMoneylineHomeOdds()).isEqualTo(-140);
    }

    @Test
    void upsertOdds_shouldIgnoreObservationAfterStartTime() {
        Map<Long, Instant> startTimes = Map.of(100L, observedAt.minusSeconds(60));

        Set<Long> changed = writer.upsertOdds(List.of(odds(100L, -120)), startTimes, observedAt);

        assertThat(changed).isEmpty();
        assertThat(oddsSnapshotRepository.findAll()).isEmpty();
    }

    @Test
    void upsertStandings_shouldInsertOncePerSnapshotDate() {
        boolean first = writer.upsertStandings(2026, LocalDate.parse("2026-07-08"), List.of(standing(1L)), observedAt);
        boolean rerun = writer.upsertStandings(2026, LocalDate.parse("2026-07-08"), List.of(standing(1L)), observedAt);

        assertThat(first).isTrue();
        assertThat(rerun).isFalse();
        assertThat(standingRepository.findBySeasonAndSnapshotDateAndTeamId(2026, LocalDate.parse("2026-07-08"), 1L))
                .isPresent();
    }

    @Test
    void upsertPlayerSeasonStats_shouldOverwriteLatestValues() {
        writer.upsertPlayerSeasonStats(2026, List.of(seasonStat(7L, "3.10")), observedAt);
        writer.upsertPlayerSeasonStats(2026, List.of(seasonStat(7L, "2.85")), observedAt.plusSeconds(60));

        BigDecimal era = playerSeasonStatRepository.findById(new PlayerSeasonStatId(2026, 7L))
                .orElseThrow()
                .getPitchingEra();
        assertThat(era).isEqualByComparingTo("2.85");
    }

    @Test
    void findByLifecycleStateIn_shouldSelectPregamePollingTargets() {
        gameRepository.save(game(100L, GameLifecycle.PREGAME_FAR.name()));
        gameRepository.save(game(101L, GameLifecycle.PREGAME_NEAR.name()));
        gameRepository.save(game(102L, GameLifecycle.LIVE.name()));
        gameRepository.save(game(103L, GameLifecycle.SCHEDULED.name()));

        List<Game> targets = gameRepository.findByLifecycleStateIn(List.of(
                GameLifecycle.PREGAME_FAR.name(),
                GameLifecycle.PREGAME_NEAR.name()
        ));

        assertThat(targets).extracting(Game::getId).containsExactlyInAnyOrder(100L, 101L);
    }

    private static Game game(long id, String lifecycleState) {
        Game game = new Game();
        game.setId(id);
        game.setStatus(Game.STATUS_SCHEDULED);
        game.setLifecycleState(lifecycleState);
        return game;
    }

    private static BdlLineup lineup(long id, long gameId, long playerId, boolean probablePitcher) {
        return new BdlLineup(
                id,
                gameId,
                new BdlLineup.Player(playerId, "Pitcher Seven", "Pitcher", "Seven", "SP"),
                new BdlLineup.TeamRef(1L),
                null,
                "SP",
                probablePitcher
        );
    }

    private static BdlOdds odds(long gameId, int moneylineHome) {
        return new BdlOdds(
                gameId,
                "book-a",
                moneylineHome,
                110,
                new BigDecimal("-1.5"),
                new BigDecimal("1.5"),
                -110,
                -110,
                new BigDecimal("8.5"),
                -105,
                -115,
                "2026-07-07T23:00:00Z"
        );
    }

    private static BdlStanding standing(long teamId) {
        return new BdlStanding(
                new BdlStanding.Team(teamId),
                "AL",
                "East",
                50,
                40,
                new BigDecimal("0.556"),
                BigDecimal.ZERO,
                new BigDecimal("72.50"),
                new BigDecimal("40.00"),
                3,
                7
        );
    }

    private static BdlPlayerSeasonStat seasonStat(long playerId, String era) {
        return new BdlPlayerSeasonStat(
                playerId,
                null,
                2026,
                new BigDecimal(era),
                new BigDecimal("2.1"),
                new BigDecimal("1.05"),
                new BigDecimal("9.80"),
                null,
                null,
                null
        );
    }
}
