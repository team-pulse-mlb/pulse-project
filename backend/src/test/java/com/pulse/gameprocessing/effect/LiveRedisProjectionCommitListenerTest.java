package com.pulse.gameprocessing.effect;

import com.pulse.gameprocessing.event.LiveScoreComputedEvent;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * LiveRedisProjectionCommitListener가 이벤트의 계산 결과를 Redis 반영으로 전달하고,
 * 반영 실패를 커밋 스레드로 전파하지 않고 격리함을 검증한다.
 */
class LiveRedisProjectionCommitListenerTest {

    private static final long GAME_ID = 5059180L;
    private static final Instant COMPUTED_AT = Instant.parse("2026-07-17T01:00:00Z");

    @Test
    void onLiveScoreComputed_forwardsProjectionToPublisher() {
        LiveSignalPublisher publisher = mock(LiveSignalPublisher.class);
        LiveRedisProjectionCommitListener listener = new LiveRedisProjectionCommitListener(publisher);

        listener.onLiveScoreComputed(sampleEvent());

        // 이벤트의 계산 값이 그대로 Redis 반영 호출로 전달되는지 고정한다.
        verify(publisher).publishLiveUpdate(
                GAME_ID, 80.0, 60, List.of("접전 흐름"),
                8, "TOP", 100L, "LIVE", List.of("접전"), COMPUTED_AT);
    }

    @Test
    void onLiveScoreComputed_isolatesPublisherFailure() {
        LiveSignalPublisher publisher = mock(LiveSignalPublisher.class);
        doThrow(new RuntimeException("Redis 반영 실패"))
                .when(publisher)
                .publishLiveUpdate(
                        anyLong(), anyDouble(), anyInt(), anyList(),
                        any(), any(), any(), any(), anyList(), any());
        LiveRedisProjectionCommitListener listener = new LiveRedisProjectionCommitListener(publisher);

        // 커밋 스레드에서 동기 실행되므로 예외가 다른 부수효과로 전파되면 안 된다.
        assertThatCode(() -> listener.onLiveScoreComputed(sampleEvent()))
                .doesNotThrowAnyException();
        verify(publisher).publishLiveUpdate(
                GAME_ID, 80.0, 60, List.of("접전 흐름"),
                8, "TOP", 100L, "LIVE", List.of("접전"), COMPUTED_AT);
    }

    private static LiveScoreComputedEvent sampleEvent() {
        return new LiveScoreComputedEvent(
                GAME_ID, COMPUTED_AT, 80.0, 80, 60, List.of("접전 흐름"), List.of("접전"),
                8, "TOP", 100L, 987654L, "LIVE", 5);
    }
}
