package com.pulse.api.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSE 중계 컴포넌트가 api 역할(웹 + pulse.sse.enabled=true)에서만 등록되는지 확인한다.
 * poller·scorer 컨테이너는 enabled=false로 두어 구독자·엔드포인트가 뜨지 않아야 한다.
 */
class SseRoleGateTest {

    private static final String[] SSE_PROPERTIES = {
            "pulse.sse.heartbeat-interval=25s",
            "pulse.sse.connection-ttl=60m",
            "pulse.sse.max-connections=1000"
    };

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withUserConfiguration(SseTestConfig.class, SseEmitterRegistry.class, RedisSignalRelay.class)
            .withPropertyValues(SSE_PROPERTIES);

    @Test
    @DisplayName("웹 컨텍스트에서 enabled=true면 SSE 컴포넌트가 등록된다")
    void shouldRegisterSseBeansWhenEnabledInWebContext() {
        webRunner.withPropertyValues("pulse.sse.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SseEmitterRegistry.class);
                    assertThat(context).hasSingleBean(RedisSignalRelay.class);
                });
    }

    @Test
    @DisplayName("enabled=false면 SSE 컴포넌트가 등록되지 않는다 (poller·scorer 역할)")
    void shouldNotRegisterSseBeansWhenDisabled() {
        webRunner.withPropertyValues("pulse.sse.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SseEmitterRegistry.class);
                    assertThat(context).doesNotHaveBean(RedisSignalRelay.class);
                });
    }

    @Test
    @DisplayName("비웹 컨텍스트(replay·rescore 배치)에서는 enabled=true여도 등록되지 않는다")
    void shouldNotRegisterSseBeansInNonWebContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(SseTestConfig.class, SseEmitterRegistry.class, RedisSignalRelay.class)
                .withPropertyValues(SSE_PROPERTIES)
                .withPropertyValues("pulse.sse.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(SseEmitterRegistry.class);
                    assertThat(context).doesNotHaveBean(RedisSignalRelay.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(SseProperties.class)
    static class SseTestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
