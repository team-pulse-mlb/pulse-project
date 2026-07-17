package com.pulse.poller;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class PollerExceptionClassifierTest {

    @Test
    @DisplayName("연결 시간 초과는 백오프 대상으로 분류한다")
    void shouldBackoff_shouldClassifyResourceAccessException() {
        ResourceAccessException exception =
                new ResourceAccessException("외부 API 연결 시간 초과", new SocketTimeoutException());

        assertThat(PollerExceptionClassifier.shouldBackoff(exception)).isTrue();
    }
}
