package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GameEventCopyCommitListenerTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-21T01:00:00Z");

    private final AiGenerationTrigger aiGenerationTrigger = mock(AiGenerationTrigger.class);
    private final GameEventCopyCommitListener listener = new GameEventCopyCommitListener(aiGenerationTrigger);

    @Test
    void onGameEventCopyRequested_shouldRequestPersistedEventCopy() {
        GameEventCopyRequestedEvent event = sampleEvent();

        listener.onGameEventCopyRequested(event);

        verify(aiGenerationTrigger).onGameEventPersisted(
                event.gameId(), event.eventId(), event.mode(), event.occurredAt());
    }

    @Test
    void onGameEventCopyRequested_shouldIsolateAiRequestFailure() {
        GameEventCopyRequestedEvent event = sampleEvent();
        doThrow(new IllegalStateException("AI 요청 실패"))
                .when(aiGenerationTrigger)
                .onGameEventPersisted(event.gameId(), event.eventId(), event.mode(), event.occurredAt());

        assertThatCode(() -> listener.onGameEventCopyRequested(event)).doesNotThrowAnyException();
    }

    private static GameEventCopyRequestedEvent sampleEvent() {
        return new GameEventCopyRequestedEvent(
                5059041L, 91L, AiGenerationTrigger.MODE_PROTECTED, OCCURRED_AT);
    }
}
