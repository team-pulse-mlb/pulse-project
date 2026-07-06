package com.pulse.api.user.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulse.api.user.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


// GlobalExceptionHandler는 Controller 안에서 발생한 예외를 JSON으로 바꿔주는 역할
/*
    But, 지금은 Spring Security 필터에서 차단 -> 인증 안되므로 Controller로 가기전에 진입 차단 됨
    그래서 Spring Security 전용 예외 처리기가 필요함

    GlobalExceptionHandler
    → Controller 안에서 발생한 예외 처리

    JwtAuthenticationEntryPoint
    → Security 단계에서 인증 실패 처리
*/
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * 인증되지 않은 사용자가 보호 API에 접근했을 때 실행됨
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        ErrorResponse errorResponse = new ErrorResponse(
                0,
                "로그인이 필요합니다."
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        objectMapper.writeValue(
                response.getWriter(),
                errorResponse
        );
    }
}