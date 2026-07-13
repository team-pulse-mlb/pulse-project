package com.pulse.api.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;

class RedisSignalRelayTest {

    private final SseEmitterRegistry emitterRegistry = mock(SseEmitterRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RedisSignalRelay relay = new RedisSignalRelay(emitterRegistry, objectMapper);

    @Test
    @DisplayName("signal:ranking 수신 시 ranking_changed 이벤트로 중계한다")
    void onMessage_shouldRelayRankingChanged() throws Exception {
        relay.onMessage(message("signal:ranking", "changed"), null);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitterRegistry).broadcast(eq("ranking_changed"), payloadCaptor.capture());
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("sequence").asLong()).isEqualTo(1L);
        assertThat(payload.get("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("signal:game:{id} 수신 시 gameId를 담아 game_updated로 중계한다")
    void onMessage_shouldRelayGameUpdated() throws Exception {
        relay.onMessage(message("signal:game:5059180", "5059180"), null);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitterRegistry).broadcast(eq("game_updated"), payloadCaptor.capture());
        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("gameId").asLong()).isEqualTo(5059180L);
        assertThat(payload.get("sequence").asLong()).isEqualTo(1L);
        assertThat(payload.get("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("sequence는 이벤트 종류별로 단조 증가한다")
    void onMessage_shouldIncreaseSequencePerEventType() throws Exception {
        relay.onMessage(message("signal:ranking", "changed"), null);
        relay.onMessage(message("signal:ranking", "changed"), null);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitterRegistry, times(2))
                .broadcast(eq("ranking_changed"), payloadCaptor.capture());
        JsonNode lastPayload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(lastPayload.get("sequence").asLong()).isEqualTo(2L);
    }

    @Test
    @DisplayName("gameId를 파싱할 수 없는 채널은 무시한다")
    void onMessage_shouldIgnoreMalformedGameChannel() {
        relay.onMessage(message("signal:game:not-a-number", ""), null);

        verifyNoInteractions(emitterRegistry);
    }

    @Test
    @DisplayName("정의되지 않은 채널은 무시한다")
    void onMessage_shouldIgnoreUnknownChannel() {
        relay.onMessage(message("signal:unknown", ""), null);

        verifyNoInteractions(emitterRegistry);
    }

    private static DefaultMessage message(String channel, String body) {
        return new DefaultMessage(
                channel.getBytes(StandardCharsets.UTF_8),
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    @Test
    @DisplayName(
            "signal:notification:{userId} 수신 시 해당 사용자에게 notification_created로 중계한다"
    )
    void onMessage_shouldRelayNotificationCreated()
            throws Exception {

        // when
        relay.onMessage(
                message(
                        "signal:notification:7",
                        "501"
                ),
                null
        );

        // then
        ArgumentCaptor<String> payloadCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emitterRegistry)
                .sendToUser(
                        eq(7L),
                        eq("notification_created"),
                        payloadCaptor.capture()
                );

        JsonNode payload =
                objectMapper.readTree(
                        payloadCaptor.getValue()
                );

        assertThat(
                payload.get("notificationId").asLong()
        ).isEqualTo(501L);
    }

    @Test
    @DisplayName(
            "사용자 ID를 파싱할 수 없는 notification 채널은 무시한다"
    )
    void onMessage_shouldIgnoreMalformedNotificationUserId() {
        relay.onMessage(
                message(
                        "signal:notification:not-a-number",
                        "501"
                ),
                null
        );

        verifyNoInteractions(emitterRegistry);
    }

    @Test
    @DisplayName(
            "notificationId를 파싱할 수 없는 알림 신호는 무시한다"
    )
    void onMessage_shouldIgnoreMalformedNotificationId() {
        relay.onMessage(
                message(
                        "signal:notification:7",
                        "not-a-number"
                ),
                null
        );

        verifyNoInteractions(emitterRegistry);
    }
}
