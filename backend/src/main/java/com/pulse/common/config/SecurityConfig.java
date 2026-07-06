package com.pulse.common.config;

import com.pulse.api.user.security.CustomUserDetailsService;
import com.pulse.api.user.security.JwtAuthenticationEntryPoint;
import com.pulse.api.user.security.jwt.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


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
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint
    ) throws Exception {

        http
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
                        // 회원가입, 로그인, 이메일 인증, 토큰 재발급, 로그아웃은 공개
                        .requestMatchers(
                                "/api/members/signup",
                                "/api/members/login",
                                "/api/members/refresh",
                                "/api/members/logout",
                                "/api/members/email/**"
                        ).permitAll()

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
}