package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinalHeadlineBackfillPropertiesTest {

    @Test
    void 경기_ID_중복을_제거하고_입력_순서를_유지한다() {
        FinalHeadlineBackfillProperties properties =
                new FinalHeadlineBackfillProperties(List.of(30L, 10L, 30L, 20L));

        assertThat(properties.gameIds()).containsExactly(30L, 10L, 20L);
    }

    @Test
    void 경기_ID가_없으면_빈_목록을_사용한다() {
        FinalHeadlineBackfillProperties properties = new FinalHeadlineBackfillProperties(null);

        assertThat(properties.gameIds()).isEmpty();
    }
}
