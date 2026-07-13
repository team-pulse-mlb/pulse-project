package com.pulse.api.sse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 중계 설정. enabled는 api 역할에서만 true로 두어
 * poller·scorer 컨테이너에서 구독자·엔드포인트가 뜨지 않게 한다.
 */
@ConfigurationProperties(prefix = "pulse.sse")
public record SseProperties(
        Duration heartbeatInterval,
        Duration connectionTtl,
        int maxConnections
) {
}
