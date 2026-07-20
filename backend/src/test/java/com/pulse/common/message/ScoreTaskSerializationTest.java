package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
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

    @Test
    void scoreTask_shouldRoundTripWithMinimalPlateAppearanceFacts() throws Exception {
        ScoreTask task = new ScoreTask(
                1L,
                Instant.parse("2026-07-08T00:00:00Z"),
                10L,
                "LIVE",
                ScoreTask.Situation.of(2, 3, 2, true, true, true),
                List.of(new ScoreTask.PlateAppearanceSnapshot(
                        7L,
                        6,
                        "top",
                        10L,
                        99L,
                        2,
                        true,
                        true,
                        true,
                        List.of(new ScoreTask.PitchSnapshot(8, 100, 92.1, 101.4, true))
                ))
        );

        ScoreTask restored = objectMapper.readValue(objectMapper.writeValueAsString(task), ScoreTask.class);

        assertThat(restored).isEqualTo(task);
    }

    @Test
    void scoreTask_shouldRoundTripWithGameSnapshot() throws Exception {
        ScoreTask.GameSnapshot gameSnapshot = new ScoreTask.GameSnapshot(7, 4, 3, true);
        ScoreTask task = new ScoreTask(
                1L,
                Instant.parse("2026-07-08T00:00:00Z"),
                10L,
                "LIVE",
                null,
                List.of(),
                gameSnapshot
        );

        ScoreTask restored = objectMapper.readValue(objectMapper.writeValueAsString(task), ScoreTask.class);

        assertThat(restored).isEqualTo(task);
        assertThat(restored.gameSnapshot()).isEqualTo(gameSnapshot);
    }

    @Test
    void scoreTask_shouldTreatMissingPlateAppearancesAsEmptyForOldMessages() throws Exception {
        String oldMessage = """
                {"gameId":1,"observedAt":"2026-07-08T00:00:00Z",
                 "lastPlayOrder":10,"lifecycleState":"LIVE","situation":null}
                """;

        ScoreTask restored = objectMapper.readValue(oldMessage, ScoreTask.class);

        assertThat(restored.plateAppearances()).isEmpty();
        assertThat(restored.gameSnapshot()).isNull();
    }
}
