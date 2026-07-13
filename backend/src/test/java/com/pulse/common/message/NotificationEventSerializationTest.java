package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pulse.common.message.NotificationEvent.NotificationType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationEventSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void notificationEvent_shouldExposeCompletedMessageAndSingleLatestTag() throws Exception {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationType.SURGE,
                1L,
                "지금 볼 만한 경기가 있어요 — 접전 흐름",
                "접전 흐름",
                Instant.parse("2026-07-10T00:00:00Z")
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.path("message").asText()).isEqualTo(event.message());
        assertThat(json.path("latestTag").asText()).isEqualTo(event.latestTag());
        assertThat(json.has("tags")).isFalse();
    }
}
