package com.pulse.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultAiCopyReaderTest {

    private final AiCopyReader reader = new DefaultAiCopyReader();

    @Test
    void returnsDefaultCopyByPurposeInProtectedMode() {
        assertAll(
                () -> assertEquals(
                        "지금 볼 만한 흐름이 감지됐습니다.",
                        reader.getCopy(1L, AiCopyPurpose.LIVE_HEADLINE, AiCopyMode.PROTECTED)
                ),
                () -> assertEquals(
                        "지금 볼 만한 흐름의 경기가 감지됐습니다.",
                        reader.getCopy(1L, AiCopyPurpose.NOTIFICATION, AiCopyMode.PROTECTED)
                ),
                () -> assertEquals(
                        "다른 경기에서 긴장감이 높아졌습니다.",
                        reader.getCopy(1L, AiCopyPurpose.SWITCH_SUGGESTION, AiCopyMode.PROTECTED)
                ),
                () -> assertEquals(
                        "다시 볼 만한 흐름이 이어진 구간입니다.",
                        reader.getCopy(1L, AiCopyPurpose.REPLAY_SUMMARY, AiCopyMode.PROTECTED)
                ),
                () -> assertEquals(
                        "다시 볼 만한 흐름이 있었던 경기입니다.",
                        reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE, AiCopyMode.PROTECTED)
                )
        );
    }

    @Test
    void returnsModeSpecificFinalHeadlineCopy() {
        assertAll(
                () -> assertEquals(
                        "다시 볼 만한 흐름이 있었던 경기입니다.",
                        reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE, AiCopyMode.PROTECTED)
                ),
                () -> assertEquals(
                        "결과를 확인한 뒤 다시 볼 만한 흐름을 돌아볼 수 있습니다.",
                        reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE, AiCopyMode.REVEALED)
                )
        );
    }

    @Test
    void returnsProtectedCopyWhenModeIsNull() {
        String copy = reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE, null);

        assertEquals("다시 볼 만한 흐름이 있었던 경기입니다.", copy);
    }

    @Test
    void returnsProtectedCopyFromTwoArgumentCompatibilityMethod() {
        String copy = reader.getCopy(1L, AiCopyPurpose.FINAL_HEADLINE);

        assertEquals("다시 볼 만한 흐름이 있었던 경기입니다.", copy);
    }

    @Test
    void returnsNonNullDefaultCopyWhenPurposeIsNull() {
        String copy = reader.getCopy(1L, null, AiCopyMode.PROTECTED);

        assertNotNull(copy);
        assertEquals("지금 볼 만한 흐름이 감지됐습니다.", copy);
    }
}