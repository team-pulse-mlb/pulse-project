package com.pulse.api.user.security.cookie;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Refresh Token HttpOnly 쿠키를 생성하는 공통 컴포넌트입니다.
 *
 * 로그인, 재발급, 로그아웃, 비밀번호 변경,
 * 회원탈퇴에서 같은 쿠키 옵션을 사용합니다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    private static final String COOKIE_NAME =
            "refreshToken";

    private final RefreshTokenCookieProperties
            refreshTokenCookieProperties;

    /**
     * 로그인·재발급 성공 시 사용할 Refresh Token 쿠키입니다.
     */
    public ResponseCookie createRefreshTokenCookie(
            String refreshToken,
            Duration maxAge
    ) {
        return ResponseCookie
                .from(
                        COOKIE_NAME,
                        refreshToken
                )
                .httpOnly(true)
                .secure(
                        refreshTokenCookieProperties.secure()
                )
                .sameSite(
                        refreshTokenCookieProperties.sameSite()
                )
                .path(
                        refreshTokenCookieProperties.path()
                )
                .maxAge(maxAge)
                .build();
    }

    /**
     * 로그아웃·비밀번호 변경·회원탈퇴 시
     * 브라우저의 Refresh Token을 삭제하는 쿠키입니다.
     */
    public ResponseCookie createDeleteRefreshTokenCookie() {
        return ResponseCookie
                .from(
                        COOKIE_NAME,
                        ""
                )
                .httpOnly(true)
                .secure(
                        refreshTokenCookieProperties.secure()
                )
                .sameSite(
                        refreshTokenCookieProperties.sameSite()
                )
                .path(
                        refreshTokenCookieProperties.path()
                )
                .maxAge(0)
                .build();
    }
}