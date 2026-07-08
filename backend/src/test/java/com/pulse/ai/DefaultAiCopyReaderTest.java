package com.pulse.ai;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DefaultAiCopyReaderTest {

    private final DefaultAiCopyReader reader = new DefaultAiCopyReader();

    @Test
    void returnsDefaultCopyByPurpose() {
        assertAll(
                () -> assertEquals(
                        "지금 볼 만한 흐름이 감지됐습니다.",
                        reader.getCopy(1L, AiCopyPurpose.LIVE_HEADLINE)
                ),
                () -> assertEquals(
                        "지금 볼 만한 흐름의 경기가 감지됐습니다.",
                        reader.getCopy(1L, AiCopyPurpose.NOTIFICATION)
                ),
                () -> assertEquals(
                        "다른 경기에서 긴장감이 높아졌습니다.",
                        reader.getCopy(1L, AiCopyPurpose.SWITCH_SUGGESTION)
                ),
                () -> assertEquals(
                        "다시 볼 만한 흐름이 이어진 구간입니다.",
                        reader.getCopy(1L, AiCopyPurpose.REPLAY_SUMMARY)
                ),
                () -> assertEquals(
                        "다시 볼 만한 흐름이 있었던 경기입니다.",
                        reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE)
                )
        );
    }

    @Test
    void returnsNonNullDefaultCopyWhenPurposeIsNull() {
        String copy = reader.getCopy(1L, null);

        assertNotNull(copy);
        assertEquals("지금 볼 만한 흐름이 감지됐습니다.", copy);
    }
}
