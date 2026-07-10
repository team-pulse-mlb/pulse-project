package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.client.BdlDtos.BdlGame;
import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.common.client.BdlDtos.BdlPlay;
import com.pulse.domain.Game;
import com.pulse.domain.GameRepository;
import com.pulse.domain.Play;
import com.pulse.domain.PlayRepository;
import com.pulse.domain.TeamRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@Import({PollerGameWriter.class, GameLifecycleStateMachine.class, PollerRunnerStateMatcher.class})
@TestPropertySource(properties = "spring.flyway.enabled=false")
class PollerGameWriterTest {

    @Autowired
    private PollerGameWriter writer;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private PlayRepository playRepository;

    @Autowired
    private TeamRepository teamRepository;

    private final Instant observedAt = Instant.parse("2026-07-08T00:00:00Z");

    @Test
    void upsertGame_shouldPersistTeamsAndOperationalSnapshot() {
        PollerGameWriter.GameUpsertResult result = writer.upsertGame(game(Game.STATUS_IN_PROGRESS), observedAt);

        Game saved = gameRepository.findById(100L).orElseThrow();
        assertThat(result.enteredLive()).isTrue();
        assertThat(saved.getSource()).isEqualTo("OPERATIONAL");
        assertThat(saved.getObservedAt()).isEqualTo(observedAt);
        assertThat(teamRepository.findById(1L)).isPresent();
        assertThat(teamRepository.findById(2L)).isPresent();
    }

    @Test
    void appendPlay_shouldIgnoreDuplicateAndUpdateGameCursor() {
        Game game = writer.upsertGame(game(Game.STATUS_IN_PROGRESS), observedAt).game();

        boolean firstAppend = writer.appendPlay(game, play(10L, 7L), observedAt);
        boolean duplicateAppend = writer.appendPlay(game, play(10L, 7L), observedAt);

        Game savedGame = gameRepository.findById(100L).orElseThrow();
        List<Play> plays = playRepository.findByGameIdOrderByPlayOrderAsc(100L);
        assertThat(firstAppend).isTrue();
        assertThat(duplicateAppend).isFalse();
        assertThat(plays).hasSize(1);
        assertThat(plays.getFirst().getBatterId()).isEqualTo(7L);
        assertThat(savedGame.getLastPlayOrder()).isEqualTo(10L);
    }

    @Test
    void updateRunnerStates_shouldPersistMatchedRunnerColumns() {
        Game game = writer.upsertGame(game(Game.STATUS_IN_PROGRESS), observedAt).game();
        writer.appendPlay(game, play(10L, 7L), observedAt);
        writer.appendPlay(game, play(11L, 8L), observedAt);
        writer.appendPlay(game, play(12L, 7L), observedAt);

        PollerRunnerStateMatcher.MatchResult result = writer.updateRunnerStates(100L, List.of(
                plateAppearance(1L, 7L, true, false, false),
                plateAppearance(2L, 8L, false, true, false),
                plateAppearance(3L, 7L, false, false, true)));

        List<Play> plays = playRepository.findByGameIdOrderByPlayOrderAsc(100L);
        assertThat(result.updates()).hasSize(3);
        assertThat(plays.getFirst().getRunnerOnFirst()).isTrue();
        assertThat(plays.get(1).getRunnerOnSecond()).isTrue();
        assertThat(plays.get(2).getRunnerOnThird()).isTrue();
    }

    private static BdlGame game(String status) {
        return new BdlGame(
                100L,
                "2026-07-08T00:00:00Z",
                status,
                1,
                new BdlGame.Team(1L, "Home", "HOM"),
                new BdlGame.Team(2L, "Away", "AWY"),
                new BdlGame.TeamData(1, List.of(1)),
                new BdlGame.TeamData(0, List.of(0))
        );
    }

    private static BdlPlay play(Long order, Long batterId) {
        return new BdlPlay(
                order,
                "at_bat",
                1,
                "Top",
                "play",
                0,
                0,
                false,
                0,
                1,
                2,
                1,
                batterId,
                99L
        );
    }

    private static BdlPlateAppearance plateAppearance(Long number, Long batterId, boolean first, boolean second,
                                                      boolean third) {
        return new BdlPlateAppearance(number, 100L, 1, "top", batterId, 99L, first, second, third);
    }
}
