package com.pulse.gameprocessing.effect;

import com.pulse.gameprocessing.event.LiveScoreComputedEvent;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SurgeCommitListenerTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant COMPUTED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void onLiveScoreComputed_shouldEvaluateAndPublishConfirmedSurge() {
        SurgeDetector detector = mock(SurgeDetector.class);
        SurgeNotificationPublisher publisher = mock(SurgeNotificationPublisher.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return null;
        }).when(detector).evaluate(eq(GAME_ID), eq(80), eq(COMPUTED_AT), any());
        SurgeCommitListener listener = new SurgeCommitListener(detector, publisher);

        listener.onLiveScoreComputed(sampleEvent());

        verify(detector).evaluate(eq(GAME_ID), eq(80), eq(COMPUTED_AT), any());
        verify(publisher).publish(
                GAME_ID, List.of("접전 흐름"), List.of("접전"), COMPUTED_AT);
    }

    @Test
    void onLiveScoreComputed_shouldIsolatePublisherFailureAfterSurgeConfirmed() {
        SurgeDetector detector = mock(SurgeDetector.class);
        SurgeNotificationPublisher publisher = mock(SurgeNotificationPublisher.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(3).run();
            return null;
        }).when(detector).evaluate(eq(GAME_ID), eq(80), eq(COMPUTED_AT), any());
        // SURGE 확정 후 알림 저장(독립 트랜잭션)이 실패해도 커밋 후 리스너 체인이 중단되지 않아야 한다.
        doThrow(new RuntimeException("알림 저장 실패"))
                .when(publisher).publish(GAME_ID, List.of("접전 흐름"), List.of("접전"), COMPUTED_AT);
        SurgeCommitListener listener = new SurgeCommitListener(detector, publisher);

        assertThatCode(() -> listener.onLiveScoreComputed(sampleEvent()))
                .doesNotThrowAnyException();

        verify(publisher).publish(GAME_ID, List.of("접전 흐름"), List.of("접전"), COMPUTED_AT);
    }

    @Test
    void onLiveScoreComputed_shouldIsolateDetectorFailure() {
        SurgeDetector detector = mock(SurgeDetector.class);
        doThrow(new RuntimeException("Redis 판정 실패"))
                .when(detector).evaluate(eq(GAME_ID), eq(80), eq(COMPUTED_AT), any());
        SurgeCommitListener listener =
                new SurgeCommitListener(detector, mock(SurgeNotificationPublisher.class));

        assertThatCode(() -> listener.onLiveScoreComputed(sampleEvent()))
                .doesNotThrowAnyException();
    }

    private static LiveScoreComputedEvent sampleEvent() {
        return new LiveScoreComputedEvent(
                GAME_ID, COMPUTED_AT, 80.0, 80, 60,
                List.of("접전 흐름"), List.of("접전"),
                8, "TOP", 100L, 987654L, "LIVE", 5);
    }
}
