package com.pulse.api.user;

import com.pulse.api.user.dto.LoginResponse;

/**
 * 로그인 서비스가 컨트롤러에 전달하는 내부 결과
 *
 * response     : JSON 응답으로 전달
 * refreshToken : HttpOnly 쿠키로 전달
 */
public record LoginResult(  // record는 DTO를 간단하게 만들기 위한 Java 문법
        LoginResponse response,
        String refreshToken
) {
}