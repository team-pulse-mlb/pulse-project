package com.pulse.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.api.user.security.jwt.JwtTokenProvider;
import jakarta.servlet.Filter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
            /*
             * 실제 JwtAuthenticationFilter를 사용하되,
             * 토큰 해석을 담당하는 JwtTokenProvider만 Mock으로 둡니다.
             *
             * Authorization 헤더가 없는 요청에서는 Provider를 사용하지 않고
             * 다음 Security 필터로 요청을 넘깁니다.
             */
            .withBean(
                    JwtAuthenticationFilter.class,
                    () -> new JwtAuthenticationFilter(
                            mock(JwtTokenProvider.class)
                    )
            )

            /*
             * 인증 실패 시 실제 401 JSON 응답이 작성되는지 확인하기 위해
             * JwtAuthenticationEntryPoint는 실제 객체를 사용합니다.
             */
            .withBean(
                    JwtAuthenticationEntryPoint.class,
                    () -> new JwtAuthenticationEntryPoint(
                            new ObjectMapper()
                    )
            );

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


    /**
     * 선수 검색 API는 외부 API 호출과 서버 자원을 사용하므로
     * 로그인하지 않은 사용자가 호출할 수 없어야 합니다.
     *
     * 이 테스트는 Controller 단독 테스트가 아니라
     * 실제 Spring Security 필터 체인을 MockMvc에 연결해서 확인합니다.
     */
    @Test
    void 선수검색Api는비로그인요청에401을반환한다() {
        webContextRunner.run(context -> {

            /*
             * Spring Security가 생성한 전체 필터 체인입니다.
             *
             * 이 필터를 MockMvc에 등록해야
             * SecurityConfig의 requestMatchers 규칙이 실제 요청에 적용됩니다.
             */
            Filter springSecurityFilterChain =
                    context.getBean(
                            "springSecurityFilterChain",
                            Filter.class
                    );

            MockMvc mockMvc =
                    MockMvcBuilders
                            .webAppContextSetup(context)
                            .addFilters(springSecurityFilterChain)
                            .build();

            /*
             * Authorization 헤더 없이 선수 검색 API를 호출합니다.
             */
            mockMvc.perform(
                            get("/api/players")
                                    .param("search", "Ohtani")
                    )
                    .andExpect(status().isUnauthorized())
                    .andExpect(
                            jsonPath("$.code")
                                    .value("UNAUTHORIZED")
                    )
                    .andExpect(
                            jsonPath("$.message")
                                    .value("로그인이 필요합니다.")
                    );
        });
    }
}
