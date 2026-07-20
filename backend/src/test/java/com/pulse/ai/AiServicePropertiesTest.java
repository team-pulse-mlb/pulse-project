package com.pulse.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiServicePropertiesTest {

    @Test
    @DisplayName("AI 서비스 설정이 없으면 기본 URL과 기본 타임아웃을 사용한다")
    void shouldUseDefaultValues() {
        // 외부 설정이 모두 누락된 상황을 재현합니다.
        AiServiceProperties properties =
                new AiServiceProperties(null, null, null);

        assertThat(properties.baseUrl())
                .isEqualTo("http://localhost:8000");
        assertThat(properties.connectTimeout())
                .isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.readTimeout())
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("사용자가 지정한 URL과 타임아웃 설정을 유지한다")
    void shouldKeepConfiguredValues() {
        // 환경변수나 application.yml에서 설정값이 전달된 상황을 재현합니다.
        AiServiceProperties properties = new AiServiceProperties(
                " https://ai.example.com/ ",
                Duration.ofSeconds(4),
                Duration.ofSeconds(45)
        );

        // URL 앞뒤 공백과 마지막 슬래시가 정리되는지도 함께 확인합니다.
        assertThat(properties.baseUrl())
                .isEqualTo("https://ai.example.com");
        assertThat(properties.connectTimeout())
                .isEqualTo(Duration.ofSeconds(4));
        assertThat(properties.readTimeout())
                .isEqualTo(Duration.ofSeconds(45));
    }
}
