package com.pulse.common.config;

import com.pulse.api.user.security.CustomUserDetailsService;
import com.pulse.api.user.security.JwtAuthenticationEntryPoint;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableConfigurationProperties(JwtProperties.class) // JwtProperties를 Spring이 관리하는 객체로 등록해주는 역할
public class SecurityConfig {

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
                        // 회원가입, 로그인, 이메일 인증, 토큰 재발급, 로그아웃은 공개
                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login",
                                "/api/members/refresh",
                                "/api/members/logout",
                                "/api/members/email/**"
                        ).permitAll()

                        // 비로그인 SSE 구독은 공개 (EventSource는 Authorization 헤더를 못 싣는다)
                        .requestMatchers("/api/sse").permitAll()

                        // 현재 로그인 사용자 확인 API는 Access Token 필요
                        .requestMatchers("/api/members/me").authenticated()

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


    // 5173 관련 임시 해결
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173"
        ));

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
