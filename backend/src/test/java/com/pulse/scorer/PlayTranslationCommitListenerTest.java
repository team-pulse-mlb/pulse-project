package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PlayTranslationCommitListener가 이벤트의 마지막 관측 순서를 트리거로 전달하고,
 * 트리거 실패를 커밋 스레드로 전파하지 않고 격리함을 검증한다.
 */
class PlayTranslationCommitListenerTest {

    private static final long GAME_ID = 5059180L;
    private static final long LAST_PLAY_ORDER = 987654L;
    private static final Instant COMPUTED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void onLiveScoreComputed_forwardsTranslationThroughPlayOrder() {
        AiGenerationTrigger trigger = mock(AiGenerationTrigger.class);
        PlayTranslationCommitListener listener = new PlayTranslationCommitListener(trigger);

        listener.onLiveScoreComputed(sampleEvent());

        verify(trigger).onPlayTranslationsPending(GAME_ID, LAST_PLAY_ORDER, COMPUTED_AT);
    }

    @Test
    void onLiveScoreComputed_isolatesTriggerFailure() {
        AiGenerationTrigger trigger = mock(AiGenerationTrigger.class);
        doThrow(new RuntimeException("번역 요청 실패"))
                .when(trigger)
                .onPlayTranslationsPending(GAME_ID, LAST_PLAY_ORDER, COMPUTED_AT);
        PlayTranslationCommitListener listener = new PlayTranslationCommitListener(trigger);

        // 커밋 스레드에서 동기 실행되므로 예외가 다른 부수효과로 전파되면 안 된다.
        assertThatCode(() -> listener.onLiveScoreComputed(sampleEvent()))
                .doesNotThrowAnyException();
        verify(trigger).onPlayTranslationsPending(GAME_ID, LAST_PLAY_ORDER, COMPUTED_AT);
    }

    private static LiveScoreComputedEvent sampleEvent() {
        return new LiveScoreComputedEvent(
                GAME_ID, COMPUTED_AT, 80.0, 80, 60, List.of("TAG"), List.of(),
                8, "TOP", 100L, LAST_PLAY_ORDER, "LIVE", 5);
    }
}
