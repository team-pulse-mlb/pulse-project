package com.pulse.common.message;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "pulse.notification-outbox")
public record NotificationOutboxProperties(
        Duration retryInitialInterval,
        Duration retryMaxInterval,
        int batchSize,
        @DefaultValue("10s") Duration confirmTimeout
) {
}
