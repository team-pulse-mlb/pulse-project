package com.pulse.replay.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.replay.migration.MigrationRows.DbPlayRow;
import com.pulse.replay.migration.MigrationRows.PlateAppearanceRow;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlateAppearancePlayMatcherTest {

    private final PlateAppearancePlayMatcher matcher = new PlateAppearancePlayMatcher();

    @Test
    @DisplayName("타순이 돌아 같은 타자가 재등장해도 batter별 k번째 PA와 그룹을 연결한다")
    void matchesRepeatedBatterByOccurrence() {
        List<DbPlayRow> plays = List.of(
                play(1, 1, "Top", 10L),
                play(2, 1, "Top", 20L),
                play(3, 1, "Top", 10L));
        List<PlateAppearanceRow> plateAppearances = List.of(
                pa(1, 1, "top", 10L, true, false, false),
                pa(2, 1, "top", 20L, false, true, false),
                pa(3, 1, "top", 10L, false, false, true));

        var result = matcher.match(plays, plateAppearances);

        assertThat(result.updates()).hasSize(3);
        assertThat(result.updates().get(0).runnerOnFirst()).isTrue();
        assertThat(result.updates().get(2).runnerOnThird()).isTrue();
        assertThat(result.unmatchedGroups()).isZero();
    }

    @Test
    @DisplayName("batter_id가 null인 play는 그룹을 끊지 않고 runner 상태도 갱신하지 않는다")
    void skipsNullBatterWithoutBreakingGroup() {
        List<DbPlayRow> plays = List.of(
                play(1, 2, "Bottom", 30L),
                play(2, 2, "Bottom", null),
                play(3, 2, "Bottom", 30L));
        List<PlateAppearanceRow> plateAppearances = List.of(
                pa(1, 2, "BOTTOM", 30L, true, true, false));

        var result = matcher.match(plays, plateAppearances);

        assertThat(result.updates()).extracting(MigrationRows.RunnerUpdate::playOrder).containsExactly(1L, 3L);
        assertThat(result.updates()).allSatisfy(update -> {
            assertThat(update.runnerOnFirst()).isTrue();
            assertThat(update.runnerOnSecond()).isTrue();
        });
    }

    @Test
    @DisplayName("half 비교는 대소문자를 정규화하고 Mid play는 매핑 대상에서 제외한다")
    void normalizesHalfAndIgnoresMid() {
        List<DbPlayRow> plays = List.of(
                play(1, 4, "Mid", 40L),
                play(2, 4, "Top", 40L));
        List<PlateAppearanceRow> plateAppearances = List.of(
                pa(1, 4, "top", 40L, false, true, true));

        var result = matcher.match(plays, plateAppearances);

        assertThat(result.updates()).hasSize(1);
        assertThat(result.updates().getFirst().playOrder()).isEqualTo(2L);
    }

    private static DbPlayRow play(long order, int inning, String half, Long batterId) {
        return new DbPlayRow(1L, order, inning, half, batterId);
    }

    private static PlateAppearanceRow pa(long number, int inning, String half, Long batterId,
                                         boolean first, boolean second, boolean third) {
        return new PlateAppearanceRow(1L, number, inning, half, batterId, null,
                first, second, third, Instant.parse("2026-07-01T00:00:00Z"));
    }
}
