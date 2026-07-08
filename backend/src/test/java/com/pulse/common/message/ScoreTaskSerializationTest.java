package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScoreTaskSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void scoreTask_shouldRoundTripWithSituation() throws Exception {
        ScoreTask task = new ScoreTask(
                1L,
                Instant.parse("2026-07-08T00:00:00Z"),
                10L,
                "LIVE",
                ScoreTask.Situation.of(2, 3, 2, true, true, true)
        );

        String json = objectMapper.writeValueAsString(task);
        ScoreTask restored = objectMapper.readValue(json, ScoreTask.class);

        assertThat(restored).isEqualTo(task);
        assertThat(restored.situation().basesLoaded()).isTrue();
        assertThat(restored.situation().scoringPosition()).isTrue();
    }

    @Test
    void scoreTask_shouldRoundTripWithNullSituation() throws Exception {
        ScoreTask task = new ScoreTask(
                1L,
                Instant.parse("2026-07-08T00:00:00Z"),
                null,
                "FINAL",
                null
        );

        String json = objectMapper.writeValueAsString(task);
        ScoreTask restored = objectMapper.readValue(json, ScoreTask.class);

        assertThat(restored.situation()).isNull();
        assertThat(restored.lastPlayOrder()).isNull();
    }
}
