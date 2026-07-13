package com.pulse.api.sse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 관리 창구. 연결 등록·해제, 전체 브로드캐스트, 주기 하트비트를 담당한다.
 * 현재는 비로그인 연결만 다루며, 인증 연결(notification_created)은
 * userId 스코프 등록 메서드를 추가하는 방식으로 확장한다(윤호 담당).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "pulse.sse", name = "enabled", havingValue = "true")
public class SseEmitterRegistry {

    private final SseProperties properties;
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    /** 비로그인 연결을 만들어 등록한다. 상한 초과 시 503으로 거절한다. */
    public SseEmitter subscribe() {
        if (emitters.size() >= properties.maxConnections()) {
            throw new SseConnectionLimitExceededException(properties.maxConnections());
        }
        SseEmitter emitter = new SseEmitter(properties.connectionTtl().toMillis());
        register(emitter);
        return emitter;
    }

    void register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(emitter::complete);
        emitter.onError(error -> emitters.remove(emitter));
        // 연결 직후 코멘트를 보내 응답 헤더를 즉시 흘려보낸다(프록시 버퍼링 대비).
        send(emitter, SseEmitter.event().comment("connected"));
    }

    /** 연결된 모든 클라이언트에 같은 이벤트를 전송한다. 전송 실패 연결은 제거한다. */
    public void broadcast(String eventName, String jsonPayload) {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().name(eventName).data(jsonPayload));
        }
    }

    /** 유휴 연결 단절 방지용 하트비트. 죽은 연결은 여기서 걸러진다. */
    @Scheduled(fixedDelayString = "${pulse.sse.heartbeat-interval:25s}")
    public void sendHeartbeats() {
        for (SseEmitter emitter : emitters) {
            send(emitter, SseEmitter.event().comment("heartbeat"));
        }
    }

    public int activeConnectionCount() {
        return emitters.size();
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 이미 끊긴 연결 정리 중 오류는 무시한다.
            }
        }
    }
}
