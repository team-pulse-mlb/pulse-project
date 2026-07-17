package com.pulse.common.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCopyResultTest {

    @Test
    void legacyConstructorShouldDefaultEvidenceListsToEmpty() {
        AiCopyResult result = new AiCopyResult(
                true,
                "event-copy-v1",
                "긴 승부가 이어졌습니다.",
                List.of(),
                false
        );

        assertThat(result.usedFactIds()).isEmpty();
        assertThat(result.usedPlayIds()).isEmpty();
    }

    @Test
    void shouldNormalizeNullListsToEmptyLists() {
        AiCopyResult result = new AiCopyResult(
                true,
                "hash-final",
                "홈팀이 5-3으로 승리",
                null,
                false,
                null,
                null
        );

        assertThat(result.violations()).isEmpty();
        assertThat(result.usedFactIds()).isEmpty();
        assertThat(result.usedPlayIds()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyEvidenceLists() {
        List<String> factIds = new ArrayList<>(
                List.of("summaryFacts.winnerSide")
        );
        List<Long> playIds = new ArrayList<>(
                List.of(312L)
        );

        AiCopyResult result = new AiCopyResult(
                true,
                "hash-final",
                "홈팀이 5-3으로 승리",
                List.of(),
                false,
                factIds,
                playIds
        );

        factIds.add("summaryFacts.walkOff");
        playIds.add(999L);

        assertThat(result.usedFactIds())
                .containsExactly("summaryFacts.winnerSide");
        assertThat(result.usedPlayIds())
                .containsExactly(312L);

        assertThatThrownBy(
                () -> result.usedFactIds().add(
                        "summaryFacts.shutout"
                )
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(
                () -> result.usedPlayIds().add(777L)
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
