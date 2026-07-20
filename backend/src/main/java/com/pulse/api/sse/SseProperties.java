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
        int anonymousMaxConnections,
        int authenticatedMaxConnections,
        int broadcastThreads,
        int broadcastQueueCapacity
) {

    public SseProperties {
        heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(25) : heartbeatInterval;
        connectionTtl = connectionTtl == null ? Duration.ofMinutes(60) : connectionTtl;
        anonymousMaxConnections = anonymousMaxConnections > 0 ? anonymousMaxConnections : 1000;
        authenticatedMaxConnections = authenticatedMaxConnections > 0
                ? authenticatedMaxConnections
                : 1000;
        broadcastThreads = broadcastThreads > 0 ? broadcastThreads : 8;
        broadcastQueueCapacity = broadcastQueueCapacity > 0 ? broadcastQueueCapacity : 1024;
    }
}
