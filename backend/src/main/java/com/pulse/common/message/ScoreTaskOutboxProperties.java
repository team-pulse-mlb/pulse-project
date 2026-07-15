package com.pulse.common.message;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.score-task-outbox")
public record ScoreTaskOutboxProperties(
        Duration retryInitialInterval,
        Duration retryMaxInterval,
        int batchSize
) {
}
