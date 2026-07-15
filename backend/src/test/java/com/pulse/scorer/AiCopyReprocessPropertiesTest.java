package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AiCopyReprocessPropertiesTest {

    @Test
    void 이벤트_배치_크기가_없으면_기본값을_사용한다() {
        AiCopyReprocessProperties properties = new AiCopyReprocessProperties(0);

        assertThat(properties.eventBatchSize()).isEqualTo(100);
    }
}
