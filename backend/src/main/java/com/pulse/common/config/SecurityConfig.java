package com.pulse.common.config;

import com.pulse.api.user.security.CustomUserDetailsService;
import com.pulse.api.user.security.JwtAuthenticationEntryPoint;
import com.pulse.api.user.security.cookie.RefreshTokenCookieProperties;
import com.pulse.api.user.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.pulse.api.user.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableConfigurationProperties({    // JwtProperties를 Spring이 관리하는 객체로 등록해주는 역할
        JwtProperties.class,
        RefreshTokenCookieProperties.class
})
public class SecurityConfig {

    /**
     * CORS 허용 오리진 목록.
     * 로컬은 Vite 개발 서버(localhost:5173)만 허용하고,
     * 배포 환경에서는 CORS_ALLOWED_ORIGINS로 프론트 커스텀 도메인을 주입한다.
     * 오리진은 스킴·호스트·포트까지 정확히 일치해야 하며, 마지막 슬래시는 넣지 않는다.
     */
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    /**
     * 이메일과 비밀번호 인증을 처리하는 객체
     */
    @Bean
    public AuthenticationManager authenticationManager(
            CustomUserDetailsService customUserDetailsService,
            PasswordEncoder passwordEncoder
    ) {

        // DB 회원 조회를 담당하는 인증 Provider
        DaoAuthenticationProvider authenticationProvider =
                new DaoAuthenticationProvider(
                        customUserDetailsService
                );

        // 회원가입 때 사용한 BCrypt와 동일한 PasswordEncoder 연결
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        return new ProviderManager(authenticationProvider);
    }

    /**
     * Spring Security의 HTTP 요청 처리 설정
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource)
                )   // 5173 페이지 오류가 아니라 CORS 오류라서 해당 코드 추가

                // React가 호출하는 REST API이므로 우선 CSRF 비활성화
                .csrf(AbstractHttpConfigurer::disable)

                // Spring Security 기본 로그인 화면 사용하지 않음
                .formLogin(AbstractHttpConfigurer::disable)

                // 브라우저 기본 인증창 사용하지 않음
                .httpBasic(AbstractHttpConfigurer::disable)

                // JWT 방식이므로 서버 세션을 만들지 않음
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // 추가 Security 예외 처리기
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                jwtAuthenticationEntryPoint
                        )
                )

                // JWT 필터를 만들기 전까지 임시로 전체 요청 허용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 5173 관련 오류 임시 해결

                        // 회원가입 Step 2 관심팀 선택 화면에서 사용하는 팀 목록 조회 API
                        // 로그인 전에도 호출해야 하므로 GET 요청은 공개 허용
                        .requestMatchers(HttpMethod.GET, "/api/teams").permitAll()
                        // 회원가입, 로그인, 이메일 인증, 토큰 재발급, 로그아웃은 공개
                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login",
                                "/api/members/refresh",
                                "/api/members/logout",
                                "/api/members/email/**"
                        ).permitAll()

                        /*
                         * SSE 1회용 토큰 발급은 로그인 사용자만 가능합니다.
                         *
                         * POST /api/sse/token
                         * - Authorization: Bearer AccessToken 필요
                         */
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/sse/token"
                        ).authenticated()

                        /*
                         * 실제 SSE 연결은 공개 경로로 둡니다.
                         *
                         * GET /api/sse
                         * - token 없음: 비로그인 연결
                         * - token 있음: Controller가 1회용 토큰을 검증
                         *
                         * EventSource는 Authorization 헤더를 직접 넣기 어렵기 때문에
                         * 이 경로 자체는 Spring Security에서 공개합니다.
                         */
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/sse"
                        ).permitAll()

                        /*
                         * 로그인한 사용자 전용 API는 Access Token이 필요합니다.
                         * - POST /api/sse/token
                         *   SSE 인증 연결용 1회용 토큰 발급
                         *
                         * /api/members/me
                         * - 현재 로그인한 회원 기본 정보 조회
                         *
                         * /api/me/**
                         * - 관심팀 및 알림 수신 설정
                         * - 알림함 목록 및 읽음 처리
                         *
                         * 현재 확정된 경로:
                         * - GET·PUT /api/me/preferences
                         * - /api/me/notifications
                         */
                        .requestMatchers(
                                "/api/members/me",
                                "/api/members/me/**",
                                "/api/me/**"
                        ).authenticated()

                        // 아직 다른 팀 API 차단 방지를 위해 나머지는 임시 허용
                        .anyRequest().permitAll()
                )

                // UsernamePasswordAuthenticationFilter보다 먼저 JWT 검사
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class  // 원래 Spring Security의 폼 로그인에서 아이디·비밀번호를 처리하는 필터
                        // 폼 로그인 보다 JWT를 쓰므로 해당 요청이 Controller로 가기전에 JWT 먼저 검사해서 정상 토큰이면 진입
                );

        return http.build();
    }


    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 허용 오리진은 설정값(app.cors.allowed-origins)에서 주입한다.
        // SSE(EventSource)도 이 CORS 설정을 따르므로 프론트 도메인이 반드시 포함돼야 한다.
        configuration.setAllowedOrigins(allowedOrigins);

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setAllowCredentials(true);

        configuration.setExposedHeaders(List.of(
                "Set-Cookie"
        ));

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }

}
