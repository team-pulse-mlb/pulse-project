package com.pulse.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.pulse.api.user.security.CustomUserDetailsService;
import com.pulse.api.user.security.JwtAuthenticationEntryPoint;
import com.pulse.api.user.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

class SecurityConfigTest {

    // SecurityConfig가 JwtProperties 바인딩을 요구하므로 최소 jwt 설정값을 넣어준다
    private static final String[] JWT_PROPERTIES = {
            "jwt.secret=test-secret",
            "jwt.access-token-expiration=10m",
            "jwt.refresh-token-expiration=1h"
    };

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withPropertyValues(JWT_PROPERTIES)
            .withUserConfiguration(SecurityConfig.class)
            .withBean(CustomUserDetailsService.class, () -> mock(CustomUserDetailsService.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class));

    // requestMatchers의 MVC 매처가 HandlerMappingIntrospector를 요구하므로 WebMvcAutoConfiguration을 함께 올린다
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class, WebMvcAutoConfiguration.class))
            .withPropertyValues(JWT_PROPERTIES)
            .withUserConfiguration(SecurityConfig.class)
            .withBean(CustomUserDetailsService.class, () -> mock(CustomUserDetailsService.class))
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(JwtAuthenticationFilter.class, () -> mock(JwtAuthenticationFilter.class))
            .withBean(JwtAuthenticationEntryPoint.class, () -> mock(JwtAuthenticationEntryPoint.class));

    @Test
    void nonWeb컨텍스트에서는Http보안빈없이인증관리자를등록한다() {
        nonWebContextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthenticationManager.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context).doesNotHaveBean(CorsConfigurationSource.class);
        });
    }

    @Test
    void servletWeb컨텍스트에서는보안필터체인을등록한다() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthenticationManager.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
            // HandlerMappingIntrospector도 CorsConfigurationSource 구현체라 타입 단언 대신 빈 이름으로 확인한다
            assertThat(context).hasBean("corsConfigurationSource");
        });
    }
}
