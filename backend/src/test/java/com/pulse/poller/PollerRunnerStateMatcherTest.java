package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.client.BdlDtos.BdlPlateAppearance;
import com.pulse.domain.Play;
import java.util.List;
import org.junit.jupiter.api.Test;

class PollerRunnerStateMatcherTest {

    private final PollerRunnerStateMatcher matcher = new PollerRunnerStateMatcher();

    @Test
    void match_shouldMapRepeatedBatterByPlateAppearanceOrder() {
        List<Play> plays = List.of(
                play(1L, 1, "Top", 10L),
                play(2L, 1, "Top", 20L),
                play(3L, 1, "Top", 10L));
        List<BdlPlateAppearance> plateAppearances = List.of(
                plateAppearance(1L, 1, "top", 10L, true, false, false),
                plateAppearance(2L, 1, "top", 20L, false, true, false),
                plateAppearance(3L, 1, "top", 10L, false, false, true));

        PollerRunnerStateMatcher.MatchResult result = matcher.match(plays, plateAppearances);

        assertThat(result.updates()).hasSize(3);
        assertThat(result.updates().getFirst().runnerOnFirst()).isTrue();
        assertThat(result.updates().get(2).runnerOnThird()).isTrue();
        assertThat(result.unmatchedGroups()).isZero();
    }

    @Test
    void match_shouldSkipPlayWithoutBatterId() {
        List<Play> plays = List.of(
                play(1L, 2, "Bottom", 30L),
                play(2L, 2, "Bottom", null),
                play(3L, 2, "Bottom", 30L));
        List<BdlPlateAppearance> plateAppearances = List.of(
                plateAppearance(1L, 2, "BOTTOM", 30L, true, true, false));

        PollerRunnerStateMatcher.MatchResult result = matcher.match(plays, plateAppearances);

        assertThat(result.updates())
                .extracting(PollerRunnerStateMatcher.RunnerStateUpdate::playOrder)
                .containsExactly(1L, 3L);
        assertThat(result.updates()).allSatisfy(update -> {
            assertThat(update.runnerOnFirst()).isTrue();
            assertThat(update.runnerOnSecond()).isTrue();
        });
    }

    private static Play play(long order, int inning, String half, Long batterId) {
        Play play = new Play();
        play.setGameId(1L);
        play.setPlayOrder(order);
        play.setInning(inning);
        play.setInningType(half);
        play.setBatterId(batterId);
        return play;
    }

    private static BdlPlateAppearance plateAppearance(
            long number,
            int inning,
            String half,
            Long batterId,
            boolean first,
            boolean second,
            boolean third
    ) {
        return new BdlPlateAppearance(number, 1L, inning, half, batterId, null, first, second, third);
    }
}
