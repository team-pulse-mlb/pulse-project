package com.pulse.api.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry(
            new SseProperties(Duration.ofSeconds(25), Duration.ofMinutes(60), 2)
    );

    @Test
    @DisplayName("subscribe는 연결을 등록하고 TTL이 설정된 emitter를 반환한다")
    void subscribe_shouldRegisterEmitter() {
        SseEmitter emitter = registry.subscribe();

        assertThat(emitter.getTimeout()).isEqualTo(Duration.ofMinutes(60).toMillis());
        assertThat(registry.activeConnectionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결 수 상한을 초과하면 subscribe를 거절한다")
    void subscribe_shouldRejectWhenLimitExceeded() {
        registry.subscribe();
        registry.subscribe();

        assertThatThrownBy(registry::subscribe)
                .isInstanceOf(SseConnectionLimitExceededException.class);
    }

    @Test
    @DisplayName("broadcast는 등록된 모든 연결에 이벤트를 전송한다")
    void broadcast_shouldSendToAllEmitters() throws IOException {
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register(first);
        registry.register(second);

        registry.broadcast("ranking_changed", "{\"sequence\":1}");

        // 등록 시 connected 코멘트 1회 + 브로드캐스트 1회
        verify(first, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송에 실패한 연결은 제거하고 정리한다")
    void broadcast_shouldRemoveBrokenEmitter() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        registry.register(broken);
        doThrow(new IOException("connection reset"))
                .when(broken).send(any(SseEmitter.SseEventBuilder.class));

        registry.broadcast("game_updated", "{\"gameId\":1}");

        assertThat(registry.activeConnectionCount()).isZero();
        verify(broken).complete();
    }

    @Test
    @DisplayName("하트비트 전송 실패로도 죽은 연결이 걸러진다")
    void sendHeartbeats_shouldRemoveBrokenEmitter() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        registry.register(broken);
        doThrow(new IllegalStateException("already completed"))
                .when(broken).send(any(SseEmitter.SseEventBuilder.class));

        registry.sendHeartbeats();

        assertThat(registry.activeConnectionCount()).isZero();
    }
}
