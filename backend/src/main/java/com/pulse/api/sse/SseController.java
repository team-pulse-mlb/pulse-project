package com.pulse.api.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 비로그인 SSE 구독 엔드포인트. ranking_changed·game_updated만 수신한다.
 * 인증 연결(?token= 소모, notification_created)은 윤호가 이 엔드포인트를 확장한다.
 */
@RestController
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "pulse.sse", name = "enabled", havingValue = "true")
public class SseController {

    private final SseEmitterRegistry emitterRegistry;

    @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                // nginx 리버스 프록시가 SSE 응답을 버퍼링하지 않게 한다.
                .header("X-Accel-Buffering", "no")
                .body(emitterRegistry.subscribe());
    }
}
