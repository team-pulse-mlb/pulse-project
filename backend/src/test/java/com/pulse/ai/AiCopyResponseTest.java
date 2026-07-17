package com.pulse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCopyResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeFinalHeadlineEvidenceFields() throws Exception {
        String json = """
                {
                  "spoilerSafe": true,
                  "contextHash": "game-5059082-final-headline-v1",
                  "safeTitle": "Ohtani의 9회 결승타",
                  "violations": [],
                  "fallbackUsed": false,
                  "usedFactIds": [
                    "summaryFacts.walkOff"
                  ],
                  "usedPlayIds": [
                    312
                  ]
                }
                """;

        AiCopyResponse response = objectMapper.readValue(
                json,
                AiCopyResponse.class
        );

        assertThat(response.spoilerSafe()).isTrue();
        assertThat(response.contextHash())
                .isEqualTo("game-5059082-final-headline-v1");
        assertThat(response.safeTitle())
                .isEqualTo("Ohtani의 9회 결승타");
        assertThat(response.violations()).isEmpty();
        assertThat(response.fallbackUsed()).isFalse();

        assertThat(response.usedFactIds())
                .containsExactly("summaryFacts.walkOff");
        assertThat(response.usedPlayIds())
                .containsExactly(312L);
    }

    @Test
    void shouldDefaultMissingEventCopyEvidenceFieldsToEmptyLists()
            throws Exception {
        String json = """
                {
                  "spoilerSafe": true,
                  "contextHash": "event-copy-v1",
                  "safeTitle": "만루에서 긴 승부가 이어졌습니다.",
                  "violations": [],
                  "fallbackUsed": false
                }
                """;

        AiCopyResponse response = objectMapper.readValue(
                json,
                AiCopyResponse.class
        );

        assertThat(response.usedFactIds()).isEmpty();
        assertThat(response.usedPlayIds()).isEmpty();
    }

    @Test
    void legacyConstructorShouldDefaultEvidenceListsToEmpty() {
        AiCopyResponse response = new AiCopyResponse(
                true,
                "event-copy-v1",
                "긴 승부가 이어졌습니다.",
                List.of(),
                false
        );

        assertThat(response.usedFactIds()).isEmpty();
        assertThat(response.usedPlayIds()).isEmpty();
    }

    @Test
    void shouldNormalizeNullListsToEmptyLists() {
        AiCopyResponse response = new AiCopyResponse(
                false,
                "hash-failed",
                null,
                null,
                false,
                null,
                null
        );

        assertThat(response.violations()).isEmpty();
        assertThat(response.usedFactIds()).isEmpty();
        assertThat(response.usedPlayIds()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyEvidenceLists() {
        List<String> factIds = new ArrayList<>(
                List.of("summaryFacts.walkOff")
        );
        List<Long> playIds = new ArrayList<>(
                List.of(312L)
        );

        AiCopyResponse response = new AiCopyResponse(
                true,
                "hash-final",
                "9회 끝내기",
                List.of(),
                false,
                factIds,
                playIds
        );

        factIds.add("summaryFacts.comebackWin");
        playIds.add(999L);

        assertThat(response.usedFactIds())
                .containsExactly("summaryFacts.walkOff");
        assertThat(response.usedPlayIds())
                .containsExactly(312L);

        assertThatThrownBy(
                () -> response.usedFactIds().add(
                        "summaryFacts.shutout"
                )
        ).isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(
                () -> response.usedPlayIds().add(777L)
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
