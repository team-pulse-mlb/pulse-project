package com.pulse.api.user;

import com.pulse.api.user.dto.*;
import com.pulse.api.user.security.cookie.RefreshTokenCookieFactory;
import com.pulse.api.user.security.cookie.RefreshTokenCookieProperties;
import com.pulse.api.user.security.jwt.JwtProperties;
import com.pulse.common.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "인증", description = "로그인, JWT 재발급과 로그아웃")
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
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    /**
     * 이메일과 비밀번호 로그인
     */
    @Operation(
            summary = "로그인",
            description = "액세스 토큰을 응답하고 리프레시 토큰을 HttpOnly 쿠키로 설정한다."
    )
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = loginService.login(request);

        ResponseCookie refreshTokenCookie =
                refreshTokenCookieFactory
                        .createRefreshTokenCookie(
                                result.refreshToken(),
                                jwtProperties
                                        .refreshTokenExpiration()
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
    @Operation(
            summary = "토큰 재발급",
            description = "refreshToken 쿠키를 회전하고 새 액세스 토큰을 응답한다."
    )
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
                refreshTokenCookieFactory
                        .createRefreshTokenCookie(
                                result.refreshToken(),
                                jwtProperties
                                        .refreshTokenExpiration()
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
    @Operation(
            summary = "로그아웃",
            description = "refreshToken을 폐기하고 해당 쿠키를 만료시킨다."
    )
    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            @CookieValue(
                    name = "refreshToken",
                    required = false
            ) String refreshToken
    ) {
        logoutService.logout(refreshToken);

        ResponseCookie deleteCookie =
                refreshTokenCookieFactory
                        .createDeleteRefreshTokenCookie();

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
    @Operation(
            summary = "현재 사용자 조회",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
    )
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
