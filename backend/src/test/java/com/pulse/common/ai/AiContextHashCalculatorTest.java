package com.pulse.common.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiContextHashCalculatorTest {

    @Test
    void 동일한_입력은_키_순서와_관계없이_동일한_해시를_만든다() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("label", "강한 타구");
        first.put("inning", 8);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("inning", 8);
        second.put("label", "강한 타구");

        assertThat(hash(AiCopyMode.PROTECTED, first)).isEqualTo(hash(AiCopyMode.PROTECTED, second));
    }

    @Test
    void 모드가_다르면_해시가_다르다() {
        assertThat(hash(AiCopyMode.PROTECTED, Map.of("label", "강한 타구")))
                .isNotEqualTo(hash(AiCopyMode.REVEALED, Map.of("label", "강한 타구")));
    }

    @Test
    void null_필드는_해시_입력에서_제외한다() {
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("label", "강한 타구");
        withNull.put("pitcher", null);

        assertThat(hash(AiCopyMode.REVEALED, withNull))
                .isEqualTo(hash(AiCopyMode.REVEALED, Map.of("label", "강한 타구")));
    }

    @Test
    void 동일한_플레이_원문은_동일한_번역_해시를_만든다() {
        String first =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        200L,
                        "Marsh struck out looking.");

        String second =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        200L,
                        "Marsh struck out looking.");

        assertThat(first)
                .isEqualTo(second);
    }

    @Test
    void 플레이_원문이_바뀌면_번역_해시도_바뀐다() {
        String original =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        200L,
                        "Marsh struck out looking.");

        String changed =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        200L,
                        "Marsh singled to center field.");

        assertThat(original)
                .isNotEqualTo(changed);
    }

    @Test
    void 플레이_ID가_바뀌면_번역_해시도_바뀐다() {
        String first =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        200L,
                        "Marsh struck out looking.");

        String second =
                AiContextHashCalculator.calculatePlayTranslation(
                        100L,
                        201L,
                        "Marsh struck out looking.");

        assertThat(first)
                .isNotEqualTo(second);
    }

    private static String hash(AiCopyMode mode, Map<String, Object> context) {
        return AiContextHashCalculator.calculate("EVENT_COPY", mode, 10L, 20L, context);
    }
}
