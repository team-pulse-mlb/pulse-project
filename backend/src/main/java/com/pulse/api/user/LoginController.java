package com.pulse.api.user;

import com.pulse.api.user.dto.*;
import com.pulse.api.user.security.jwt.JwtProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/members")
public class LoginController {

    private final LoginService loginService;
    private final JwtProperties jwtProperties;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;

    /**
     * 이메일과 비밀번호 로그인
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = loginService.login(request);

        ResponseCookie refreshTokenCookie =
                ResponseCookie.from(
                        "refreshToken",
                        result.refreshToken()
                )
                        // JavaScript에서 쿠키를 읽지 못하도록 설정
                        .httpOnly(true)

                        // 현재 Localhost가 HTTP이므로 false
                        // 실제 HTTPS 배포 환경에서는 true로 변경
                        // 로컬이 8080이므로 일단 false. / ★★★★★★★ 배포 환경에서 HTTPS를 적용하면 true로 변경 ★★★★★★★
                        .secure(false)

                        // 로컬 개발 환경용
                        // 쿠키가 다른 사이트의 요청에 무분별하게 포함되는 것을 제한
                        .sameSite("Lax")

                        // 회원 인증 API에만 쿠키 전송
                        // Refresh Token 쿠키를 모든 API에 보내지 않고 다음 범위에만 보내게 함
                        .path("/api/members")

                        // Refresh Token과 쿠키 만료시간을 동일하게 설정
                        .maxAge(
                                jwtProperties.refreshTokenExpiration()
                        )

                        .build();

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
                ResponseCookie.from(
                                "refreshToken",
                                result.refreshToken()
                        )
                        // JavaScript에서 쿠키를 읽지 못하도록 설정
                        .httpOnly(true)

                        // 현재 Localhost가 HTTP이므로 false
                        // ***** 실제 HTTPS 배포 환경에서는 true로 변경 *****
                        .secure(false)

                        // 로컬 개발 환경용
                        .sameSite("Lax")

                        // refreshToken 쿠키는 회원 인증 API 범위에서만 사용
                        .path("/api/members")

                        // Refresh Token과 쿠키 만료시간을 동일하게 설정
                        .maxAge(
                                jwtProperties.refreshTokenExpiration()
                        )

                        .build();

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
                ResponseCookie.from("refreshToken", "")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .path("/api/members")
                        .maxAge(0)
                        .build();

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


}