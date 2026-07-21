package com.pulse.gameprocessing.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LatestTagSelectorTest {

    private final LatestTagSelector selector = new LatestTagSelector();

    @Test
    void select_shouldPreferLastNewlyActivatedTagWithoutRedisState() {
        LatestTagSelector.Selection selection = selector.select(
                List.of("접전 흐름", "득점권 압박", "풀카운트 승부"),
                List.of("접전 흐름"),
                null);

        assertThat(selection.tag()).isEqualTo("풀카운트 승부");
        assertThat(selection.newlyActivated()).isTrue();
    }

    @Test
    void select_shouldKeepPreviousLatestTagWhileItRemainsActive() {
        LatestTagSelector.Selection selection = selector.select(
                List.of("접전 흐름", "득점권 압박"),
                List.of("접전 흐름", "득점권 압박"),
                "접전 흐름");

        assertThat(selection.tag()).isEqualTo("접전 흐름");
        assertThat(selection.newlyActivated()).isFalse();
    }
}
