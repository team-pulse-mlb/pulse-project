package com.pulse.common.message;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.notification-outbox")
public record NotificationOutboxProperties(
        Duration retryInitialInterval,
        Duration retryMaxInterval,
        int batchSize
) {
}
