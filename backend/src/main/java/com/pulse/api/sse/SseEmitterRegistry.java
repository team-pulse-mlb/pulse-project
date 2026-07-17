package com.pulse.api.sse;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 연결 관리 창구입니다.
 *
 * 담당 기능:
 *
 * 1. 비로그인 SSE 연결 등록
 * 2. 로그인 사용자별 SSE 연결 등록
 * 3. 모든 연결 대상 이벤트 방송
 * 4. 특정 사용자 대상 notification_created 전송
 * 5. 연결 종료·오류·타임아웃 정리
 * 6. 주기적인 하트비트 전송
 *
 * 연결 구조:
 *
 * emitters
 * - 비로그인·로그인 연결을 모두 저장
 * - ranking_changed, game_updated 전체 방송에 사용
 *
 * userEmitters
 * - 로그인 연결만 userId 기준으로 저장
 * - notification_created 사용자별 전송에 사용
 */
@Slf4j
@Component
@ConditionalOnWebApplication(
        type = ConditionalOnWebApplication.Type.SERVLET
)
@ConditionalOnProperty(
        prefix = "pulse.sse",
        name = "enabled",
        havingValue = "true"
)
public class SseEmitterRegistry {

    private final SseProperties properties;
    private final ThreadPoolExecutor broadcastExecutor;

    /**
     * 현재 살아 있는 전체 SSE 연결입니다.
     *
     * 비로그인 연결과 로그인 연결을 모두 포함합니다.
     */
    private final Set<SseEmitter> emitters =
            ConcurrentHashMap.newKeySet();

    /**
     * 로그인 사용자의 SSE 연결을 userId별로 관리합니다.
     *
     * 한 사용자가 여러 브라우저 탭 또는 기기로 접속할 수 있으므로
     * userId 하나에 여러 SseEmitter가 연결될 수 있습니다.
     */
    private final ConcurrentMap<Long, Set<SseEmitter>> userEmitters =
            new ConcurrentHashMap<>();

    /**
     * 인증된 emitter가 어느 사용자 소유인지 저장합니다.
     *
     * 연결 실패 또는 종료 시 해당 사용자의 Set에서도
     * 빠르게 제거하기 위해 사용합니다.
     */
    private final ConcurrentMap<SseEmitter, Long> emitterOwners =
            new ConcurrentHashMap<>();

    public SseEmitterRegistry(SseProperties properties) {
        this.properties = properties;
        this.broadcastExecutor = new ThreadPoolExecutor(
                properties.broadcastThreads(),
                properties.broadcastThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.broadcastQueueCapacity()),
                broadcastThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 비로그인 SSE 연결을 생성하고 등록합니다.
     *
     * 비로그인 연결은:
     *
     * - ranking_changed
     * - game_updated
     *
     * 이벤트만 받습니다.
     *
     * @return 새 SSE 연결
     */
    public synchronized SseEmitter subscribe() {
        validateAnonymousConnectionLimit();

        SseEmitter emitter =
                createEmitter();

        register(emitter);

        return emitter;
    }

    /**
     * 로그인 사용자의 SSE 연결을 생성하고 등록합니다.
     *
     * 로그인 연결은 전체 연결에도 등록되므로:
     *
     * - ranking_changed
     * - game_updated
     *
     * 이벤트를 받을 수 있습니다.
     *
     * 사용자별 연결에도 등록되므로:
     *
     * - notification_created
     *
     * 이벤트도 받을 수 있습니다.
     *
     * @param userId 로그인 사용자 PK
     * @return 새 SSE 연결
     */
    public synchronized SseEmitter subscribe(long userId) {
        validateAuthenticatedConnectionLimit();

        SseEmitter emitter =
                createEmitter();

        register(
                userId,
                emitter
        );

        return emitter;
    }

    /**
     * 비로그인 연결을 전체 연결 목록에 등록합니다.
     *
     * package-private으로 둔 이유:
     * 테스트에서 Mock SseEmitter를 직접 등록하기 위함입니다.
     */
    void register(SseEmitter emitter) {
        emitters.add(emitter);

        registerCallbacks(emitter);

        /*
         * 연결 직후 코멘트를 보내 응답 헤더를 즉시 흘려보냅니다.
         * nginx 등의 프록시가 응답을 버퍼링하지 않도록 돕습니다.
         */
        send(
                emitter,
                SseEmitter.event()
                        .comment("connected")
        );
    }

    /**
     * 로그인 사용자의 연결을 등록합니다.
     *
     * 같은 emitter를:
     *
     * 1. 전체 연결 Set
     * 2. 사용자별 연결 Map
     *
     * 두 곳에 저장합니다.
     *
     * @param userId 로그인 사용자 PK
     * @param emitter 등록할 SSE 연결
     */
    void register(
            long userId,
            SseEmitter emitter
    ) {
        emitters.add(emitter);

        /*
         * 같은 사용자의 SSE 연결 등록과 Map 갱신을
         * 하나의 compute 연산 안에서 처리합니다.
         *
         * 기존 computeIfAbsent(...).add(...) 방식은
         * Map 조회와 Set 추가가 분리되어 있기 때문에,
         * 기존 연결이 제거되는 순간 새 연결이 등록되면
         * 새 연결이 들어 있는 Set이 Map에서 빠질 수 있습니다.
         */
        userEmitters.compute(
                userId,
                (ignored, connections) -> {
                    Set<SseEmitter> updatedConnections =
                            connections == null
                                    ? ConcurrentHashMap.newKeySet()
                                    : connections;

                    updatedConnections.add(emitter);

                    return updatedConnections;
                }
        );

        emitterOwners.put(
                emitter,
                userId
        );

        registerCallbacks(emitter);

        send(
                emitter,
                SseEmitter.event()
                        .comment("connected")
        );
    }

    /**
     * 연결된 모든 클라이언트에 이벤트를 전송합니다.
     *
     * 사용 대상:
     *
     * - ranking_changed
     * - game_updated
     */
    public void broadcast(
            String eventName,
            String jsonPayload
    ) {
        int rejectedCount = 0;
        for (SseEmitter emitter : emitters) {
            try {
                broadcastExecutor.execute(() -> send(
                        emitter,
                        SseEmitter.event()
                                .name(eventName)
                                .data(jsonPayload)
                ));
            } catch (RejectedExecutionException exception) {
                rejectedCount++;
            }
        }
        if (rejectedCount > 0) {
            log.warn(
                    "SSE broadcast 큐가 가득 차 일부 전송을 건너뜁니다. eventName={}, rejectedCount={}",
                    eventName,
                    rejectedCount);
        }
    }

    /**
     * 특정 로그인 사용자의 모든 연결에만 이벤트를 전송합니다.
     *
     * 한 사용자가 브라우저 탭을 여러 개 열었으면
     * 해당 사용자의 모든 탭에 이벤트가 전달됩니다.
     *
     * 사용 대상:
     *
     * - notification_created
     *
     * @param userId 이벤트를 받을 사용자 PK
     * @param eventName SSE 이벤트 이름
     * @param jsonPayload JSON 문자열 payload
     */
    public void sendToUser(
            long userId,
            String eventName,
            String jsonPayload
    ) {
        Set<SseEmitter> connections =
                userEmitters.get(userId);

        /*
         * 해당 사용자가 현재 SSE에 연결돼 있지 않으면
         * 전송하지 않고 정상 종료합니다.
         *
         * 알림은 이미 DB에 저장됐기 때문에,
         * 사용자가 나중에 알림 목록을 조회하면 확인할 수 있습니다.
         */
        if (connections == null || connections.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : connections) {
            send(
                    emitter,
                    SseEmitter.event()
                            .name(eventName)
                            .data(jsonPayload)
            );
        }
    }

    /**
     * 유휴 연결이 프록시나 브라우저에서 끊기지 않도록
     * 주기적으로 SSE 코멘트를 보냅니다.
     */
    @Scheduled(
            fixedDelayString =
                    "${pulse.sse.heartbeat-interval:25s}"
    )
    public void sendHeartbeats() {
        for (SseEmitter emitter : emitters) {
            send(
                    emitter,
                    SseEmitter.event()
                            .comment("heartbeat")
            );
        }
    }

    /**
     * 전체 활성 SSE 연결 수를 반환합니다.
     *
     * 비로그인 연결과 로그인 연결을 모두 포함합니다.
     */
    public int activeConnectionCount() {
        return emitters.size();
    }

    /**
     * 특정 사용자의 활성 SSE 연결 수를 반환합니다.
     *
     * 주로 테스트와 운영 확인용으로 사용합니다.
     */
    public int activeUserConnectionCount(long userId) {
        Set<SseEmitter> connections =
                userEmitters.get(userId);

        return connections == null
                ? 0
                : connections.size();
    }

    /**
     * 설정된 최대 연결 수를 초과했는지 검사합니다.
     */
    private void validateAnonymousConnectionLimit() {
        if (activeAnonymousConnectionCount() >= properties.anonymousMaxConnections()) {
            throw new SseConnectionLimitExceededException(
                    properties.anonymousMaxConnections()
            );
        }
    }

    private void validateAuthenticatedConnectionLimit() {
        if (emitterOwners.size() >= properties.authenticatedMaxConnections()) {
            throw new SseConnectionLimitExceededException(
                    properties.authenticatedMaxConnections()
            );
        }
    }

    int activeAnonymousConnectionCount() {
        return emitters.size() - emitterOwners.size();
    }

    @PreDestroy
    void shutdownBroadcastExecutor() {
        broadcastExecutor.shutdownNow();
    }

    private static ThreadFactory broadcastThreadFactory() {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "sse-broadcast-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * application.yml의 connection-ttl을 적용해
     * 새로운 SseEmitter를 생성합니다.
     */
    private SseEmitter createEmitter() {
        return new SseEmitter(
                properties.connectionTtl().toMillis()
        );
    }

    /**
     * 연결 완료·타임아웃·오류 콜백을 등록합니다.
     */
    private void registerCallbacks(
            SseEmitter emitter
    ) {
        emitter.onCompletion(
                () -> removeEmitter(emitter)
        );

        emitter.onTimeout(() -> {
            removeEmitter(emitter);
            emitter.complete();
        });

        emitter.onError(
                error -> removeEmitter(emitter)
        );
    }

    /**
     * SSE 이벤트를 실제 연결로 전송합니다.
     *
     * 전송 실패 시 죽은 연결을 전체 목록과
     * 사용자별 목록에서 모두 제거합니다.
     */
    private void send(
            SseEmitter emitter,
            SseEmitter.SseEventBuilder event
    ) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            removeEmitter(emitter);

            try {
                emitter.complete();
            } catch (Exception ignored) {
                /*
                 * 이미 종료된 연결을 정리하면서 발생한 오류는
                 * 추가로 처리하지 않습니다.
                 */
            }
        }
    }

    /**
     * SSE 연결을 모든 관리 자료구조에서 제거합니다.
     */
    private synchronized void removeEmitter(
            SseEmitter emitter
    ) {
        emitters.remove(emitter);

        Long userId =
                emitterOwners.remove(emitter);

        /*
         * 비로그인 연결은 emitterOwners에 없으므로
         * 전체 Set에서만 제거하면 됩니다.
         */
        if (userId == null) {
            return;
        }

        /*
         * 같은 사용자의 연결 제거와 Map 항목 정리를
         * 하나의 computeIfPresent 연산 안에서 처리합니다.
         *
         * 등록과 제거가 모두 userId 기준 compute 연산을 사용하므로,
         * 기존 연결을 제거하는 순간 새 탭의 연결이 등록되더라도
         * 새 연결이 들어 있는 사용자 매핑 전체가 삭제되지 않습니다.
         */
        userEmitters.computeIfPresent(
                userId,
                (ignored, connections) -> {
                    connections.remove(emitter);

                    /*
                     * 연결이 하나도 남지 않았으면 null을 반환해
                     * 해당 userId의 Map 항목을 제거합니다.
                     */
                    return connections.isEmpty()
                            ? null
                            : connections;
                }
        );
    }
}
