package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class ReasonTagsTest {

    @Test
    void from_shouldUseProtectedLabelsAndExcludeRetiredCountTag() {
        Map<String, Double> signals = new LinkedHashMap<>();
        signals.put("recent_score", 5.0);
        signals.put("count_pressure", 5.0);

        assertThat(ReasonTags.from(signals))
                .containsExactly("흐름 변화")
                .allMatch(tag -> !tag.contains("점수"));
    }

    @Test
    @DisplayName("풀카운트 성분이 포함되면 풀카운트 승부 태그를 추가한다")
    void fromAddsFullCountTagWhenIncluded() {
        Map<String, Double> signals = Map.of("count_pressure", 3.0);

        assertThat(ReasonTags.from(signals, true)).containsExactly("풀카운트 승부");
    }

    @Test
    @DisplayName("2아웃 단독 카운트 압박에는 풀카운트 태그를 추가하지 않는다")
    void fromDoesNotAddFullCountTagForTwoOutsOnly() {
        Map<String, Double> signals = Map.of("count_pressure", 3.0);

        assertThat(ReasonTags.from(signals, false)).isEmpty();
    }
}
