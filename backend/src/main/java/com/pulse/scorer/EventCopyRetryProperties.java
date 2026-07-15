package com.pulse.scorer;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 누락된 AI 이벤트 문구를 재시도하는 스케줄 설정이다. */
@ConfigurationProperties(prefix = "pulse.ai.event-copy-retry")
public record EventCopyRetryProperties(
        Duration delay,
        Duration window,
        int maxAttempts,
        int batchSize
) {
}
