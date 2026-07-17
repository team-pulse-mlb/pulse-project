package com.pulse.api.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    private final SseEmitterRegistry registry = new SseEmitterRegistry(
            new SseProperties(Duration.ofSeconds(25), Duration.ofMinutes(60), 2, 2, 2, 16)
    );

    @AfterEach
    void tearDown() {
        registry.shutdownBroadcastExecutor();
    }

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
        verify(first, timeout(1000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, timeout(1000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("전송에 실패한 연결은 제거하고 정리한다")
    void broadcast_shouldRemoveBrokenEmitter() throws IOException {
        SseEmitter broken = mock(SseEmitter.class);
        registry.register(broken);
        doThrow(new IOException("connection reset"))
                .when(broken).send(any(SseEmitter.SseEventBuilder.class));

        registry.broadcast("game_updated", "{\"gameId\":1}");

        verify(broken, timeout(1000)).complete();
        assertThat(registry.activeConnectionCount()).isZero();
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

    @Test
    @DisplayName("로그인 subscribe는 전체 연결과 사용자별 연결에 함께 등록한다")
    void authenticatedSubscribe_shouldRegisterUserEmitter() {
        SseEmitter emitter =
                registry.subscribe(7L);

        assertThat(emitter.getTimeout())
                .isEqualTo(
                        Duration.ofMinutes(60).toMillis()
                );

        assertThat(registry.activeConnectionCount())
                .isEqualTo(1);

        assertThat(registry.activeUserConnectionCount(7L))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("sendToUser는 해당 사용자의 연결에만 이벤트를 전송한다")
    void sendToUser_shouldSendOnlyToTargetUser()
            throws IOException {

        SseEmitter firstUserEmitter =
                mock(SseEmitter.class);

        SseEmitter secondUserEmitter =
                mock(SseEmitter.class);

        registry.register(
                7L,
                firstUserEmitter
        );

        registry.register(
                8L,
                secondUserEmitter
        );

        registry.sendToUser(
                7L,
                "notification_created",
                "{\"notificationId\":501}"
        );

        /*
         * 사용자 7:
         * connected 코멘트 1회
         * notification_created 1회
         */
        verify(firstUserEmitter, times(2))
                .send(
                        any(
                                SseEmitter.SseEventBuilder.class
                        )
                );

        /*
         * 사용자 8:
         * connected 코멘트만 1회
         */
        verify(secondUserEmitter, times(1))
                .send(
                        any(
                                SseEmitter.SseEventBuilder.class
                        )
                );
    }

    @Test
    @DisplayName("로그인 연결도 ranking_changed 전체 방송을 수신한다")
    void broadcast_shouldAlsoSendToAuthenticatedEmitter()
            throws IOException {

        SseEmitter anonymousEmitter =
                mock(SseEmitter.class);

        SseEmitter authenticatedEmitter =
                mock(SseEmitter.class);

        registry.register(anonymousEmitter);

        registry.register(
                7L,
                authenticatedEmitter
        );

        registry.broadcast(
                "ranking_changed",
                "{\"sequence\":1}"
        );

        /*
         * 두 연결 모두:
         * connected 코멘트 1회
         * ranking_changed 1회
         */
        verify(anonymousEmitter, timeout(1000).times(2))
                .send(
                        any(
                                SseEmitter.SseEventBuilder.class
                        )
                );

        verify(authenticatedEmitter, timeout(1000).times(2))
                .send(
                        any(
                                SseEmitter.SseEventBuilder.class
                        )
                );
    }

    @Test
    @DisplayName("느린 연결이 있어도 broadcast 호출과 다른 연결 전송은 막히지 않는다")
    void broadcast_shouldIsolateSlowEmitter() throws Exception {
        SseEmitter slow = mock(SseEmitter.class);
        SseEmitter fast = mock(SseEmitter.class);
        registry.register(slow);
        registry.register(fast);
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            slowStarted.countDown();
            releaseSlow.await(2, TimeUnit.SECONDS);
            return null;
        }).when(slow).send(any(SseEmitter.SseEventBuilder.class));

        long startedAt = System.nanoTime();
        registry.broadcast("ranking_changed", "{\"sequence\":1}");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        try {
            assertThat(elapsedMillis).isLessThan(500);
            assertThat(slowStarted.await(1, TimeUnit.SECONDS)).isTrue();
            verify(fast, timeout(1000).times(2)).send(any(SseEmitter.SseEventBuilder.class));
        } finally {
            releaseSlow.countDown();
        }
    }

    @Test
    @DisplayName("bounded queue가 가득 차도 broadcast 호출 스레드에서 전송하지 않는다")
    void broadcast_shouldNotRunRejectedTaskOnCallerThread() throws Exception {
        SseEmitterRegistry saturatedRegistry = new SseEmitterRegistry(
                new SseProperties(Duration.ofSeconds(25), Duration.ofMinutes(60), 3, 3, 1, 1));
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        SseEmitter third = mock(SseEmitter.class);
        saturatedRegistry.register(first);
        saturatedRegistry.register(second);
        saturatedRegistry.register(third);
        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        for (SseEmitter emitter : java.util.List.of(first, second, third)) {
            org.mockito.Mockito.doAnswer(invocation -> {
                sendStarted.countDown();
                releaseSend.await(2, TimeUnit.SECONDS);
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        }

        long startedAt = System.nanoTime();
        saturatedRegistry.broadcast("game_updated", "{\"gameId\":1}");
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        try {
            assertThat(elapsedMillis).isLessThan(500);
            assertThat(sendStarted.await(1, TimeUnit.SECONDS)).isTrue();
        } finally {
            releaseSend.countDown();
            saturatedRegistry.shutdownBroadcastExecutor();
        }
    }

    @Test
    @DisplayName("익명 연결 상한이 가득 차도 인증 연결 quota는 소진되지 않는다")
    void subscribe_shouldSeparateAnonymousAndAuthenticatedQuotas() {
        registry.subscribe();
        registry.subscribe();

        assertThatThrownBy(registry::subscribe)
                .isInstanceOf(SseConnectionLimitExceededException.class);

        registry.subscribe(7L);
        registry.subscribe(8L);

        assertThat(registry.activeConnectionCount()).isEqualTo(4);
        assertThat(registry.activeAnonymousConnectionCount()).isEqualTo(2);
        assertThatThrownBy(() -> registry.subscribe(9L))
                .isInstanceOf(SseConnectionLimitExceededException.class);
    }
}
