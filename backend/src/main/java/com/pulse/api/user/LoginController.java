package com.pulse.api.user;

import com.pulse.api.user.dto.*;
import com.pulse.api.user.security.cookie.RefreshTokenCookieProperties;
import com.pulse.api.user.security.jwt.JwtProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class LoginController {

    private final LoginService loginService;
    private final JwtProperties jwtProperties;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;

    /*
     * refreshToken 쿠키 옵션 설정입니다.
     *
     * 로컬/배포 환경에 따라 secure, sameSite, path 값을 다르게 적용하기 위해 사용합니다.
     */
    private final RefreshTokenCookieProperties refreshTokenCookieProperties;

    /**
     * 이메일과 비밀번호 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = loginService.login(request);

        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(
                        result.refreshToken(),
                        jwtProperties.refreshTokenExpiration()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(result.response());
    }


    // 토큰 재발급
    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refreshAccessToken(
            @CookieValue(   // @CookieValue는 요청 쿠키에서 refreshToken 값을 꺼내오는 역할
                    name = "refreshToken",
                    required = false
            ) String refreshToken
    ) {
        // Refresh Token 검증 후
        // 새 Access Token + 새 Refresh Token을 발급받는다.
        TokenRefreshResult result =
                tokenRefreshService.refreshAccessToken(
                        refreshToken
                );

        // 새 Refresh Token을 HttpOnly Cookie로 다시 내려보낸다.
        //
        // 이 부분이 Refresh Token Rotation의 Controller 핵심이다.
        // 기존 Cookie에 들어 있던 Refresh Token을 새 Refresh Token으로 교체한다.
        ResponseCookie refreshTokenCookie =
                createRefreshTokenCookie(
                        result.refreshToken(),
                        jwtProperties.refreshTokenExpiration()
                );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        refreshTokenCookie.toString()
                )
                .body(result.response());
    }


    // 로그아웃 처리
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String refreshToken
    ) {
        logoutService.logout(refreshToken);

        ResponseCookie deleteCookie =
                createDeleteRefreshTokenCookie();

        LogoutResponse response = new LogoutResponse(
                "SUCCESS",
                "로그아웃되었습니다."
        );

        return ResponseEntity
                .ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        deleteCookie.toString()
                )
                .body(response);
    }


    // 테스트용
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            Authentication authentication   // 우리가 JwtAuthenticationFilter에서 넣어준 인증 정보
    ) {
        String email = authentication.getName();

        List<String> roles = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        MeResponse response = new MeResponse(
                "SUCCESS",
                email,
                roles
        );

        return ResponseEntity.ok(response);
    }


    /*
     * refreshToken을 담은 HttpOnly 쿠키를 생성합니다.
     *
     * 로그인 성공, 토큰 재발급 성공 시 공통으로 사용합니다.
     *
     * 중요한 점:
     * - httpOnly=true
     *   → JavaScript에서 refreshToken을 읽을 수 없게 막습니다.
     *
     * - secure
     *   → 로컬에서는 false, HTTPS 배포에서는 true로 설정합니다.
     *   → application.yml / 환경변수로 제어합니다.
     *
     * - sameSite
     *   → 현재 기본값은 Lax입니다.
     *   → 배포 구조에 따라 None이 필요할 수 있습니다.
     *
     * - path
     *   → refreshToken 쿠키가 전송될 API 범위를 제한합니다.
     */
    private ResponseCookie createRefreshTokenCookie(
            String refreshToken,
            Duration maxAge
    ) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(refreshTokenCookieProperties.secure())
                .sameSite(refreshTokenCookieProperties.sameSite())
                .path(refreshTokenCookieProperties.path())
                .maxAge(maxAge)
                .build();
    }

    /*
     * refreshToken 삭제용 쿠키를 생성합니다.
     *
     * 쿠키를 삭제할 때도 생성할 때와 같은 옵션을 맞춰야 합니다.
     * 특히 path, secure, sameSite가 달라지면 브라우저가 기존 쿠키를 삭제하지 못할 수 있습니다.
     */
    private ResponseCookie createDeleteRefreshTokenCookie() {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(refreshTokenCookieProperties.secure())
                .sameSite(refreshTokenCookieProperties.sameSite())
                .path(refreshTokenCookieProperties.path())
                .maxAge(0)
                .build();
    }


}