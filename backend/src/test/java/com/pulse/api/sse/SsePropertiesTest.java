package com.pulse.api.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SsePropertiesTest {

    @Test
    @DisplayName("broadcast executor와 연결 quota에 합리적인 기본값을 적용한다")
    void constructor_shouldApplyDefaults() {
        SseProperties properties = new SseProperties(null, null, 0, 0, 0, 0);

        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(25));
        assertThat(properties.connectionTtl()).isEqualTo(Duration.ofMinutes(60));
        assertThat(properties.anonymousMaxConnections()).isEqualTo(1000);
        assertThat(properties.authenticatedMaxConnections()).isEqualTo(1000);
        assertThat(properties.broadcastThreads()).isEqualTo(8);
        assertThat(properties.broadcastQueueCapacity()).isEqualTo(1024);
    }
}
