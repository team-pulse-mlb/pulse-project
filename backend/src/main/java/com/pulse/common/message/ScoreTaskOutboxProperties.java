package com.pulse.common.message;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pulse.score-task-outbox")
public record ScoreTaskOutboxProperties(
        Duration retryInitialInterval,
        Duration retryMaxInterval,
        int batchSize,
        @DefaultValue("10s") Duration confirmTimeout,
        @DefaultValue("7d") Duration retentionPeriod,
        @DefaultValue("500") int cleanupBatchSize
) {
}
