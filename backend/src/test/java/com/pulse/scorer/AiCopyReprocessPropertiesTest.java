package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiCopyReprocessPropertiesTest {

    @Test
    void 이벤트_배치_크기가_없으면_기본값을_사용한다() {
        AiCopyReprocessProperties properties =
                new AiCopyReprocessProperties(0, null, null, null, null, null, null);

        assertThat(properties.eventBatchSize()).isEqualTo(100);
        assertThat(properties.runBackfill()).isTrue();
        assertThat(properties.runHeadline()).isTrue();
        assertThat(properties.runEventCopy()).isTrue();
        assertThat(properties.runPlayTranslation()).isTrue();
    }

    @Test
    void 시작일과_종료일이_모두_비어있으면_전체_대상이다() {
        AiCopyReprocessProperties properties =
                new AiCopyReprocessProperties(50, "", "", true, true, true, true);

        assertThat(properties.hasWindow()).isFalse();
    }

    @Test
    void 시작일과_종료일이_모두_있으면_기간_한정_대상이다() {
        AiCopyReprocessProperties properties = new AiCopyReprocessProperties(
                50, "2026-07-13", "2026-07-16", true, true, true, true);

        assertThat(properties.hasWindow()).isTrue();
    }

    @Test
    void 시작일만_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> new AiCopyReprocessProperties(
                50, "2026-07-13", null, true, true, true, true))
                .isInstanceOf(IllegalStateException.class);
    }
}
