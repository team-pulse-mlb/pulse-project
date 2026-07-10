package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

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
}
