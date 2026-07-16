package com.pulse.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BdlPropertiesTest {

    @Test
    @DisplayName("타임아웃 설정이 없으면 합리적인 기본값을 사용한다")
    void shouldUseDefaultTimeouts() {
        BdlProperties properties = new BdlProperties("https://api.balldontlie.io", "키", null, null);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("설정한 연결 및 읽기 타임아웃을 유지한다")
    void shouldKeepConfiguredTimeouts() {
        BdlProperties properties = new BdlProperties(
                "https://api.balldontlie.io",
                "키",
                Duration.ofSeconds(3),
                Duration.ofSeconds(12)
        );

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(12));
    }
}
