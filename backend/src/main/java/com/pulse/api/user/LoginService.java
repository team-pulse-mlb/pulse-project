package com.pulse.api.user;

import com.pulse.api.user.dto.LoginRequest;
import com.pulse.api.user.dto.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.pulse.api.user.exception.LoginFailedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import com.pulse.api.user.security.jwt.JwtTokenProvider;
import org.springframework.security.core.GrantedAuthority;
import com.pulse.api.user.security.RefreshTokenService;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    /**
     * 이메일과 비밀번호 로그인
     */
    public LoginResult login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 아직 인증되지 않은 이메일·비밀번호 정보를 생성
        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                );


        try {
            // Spring Security에 실제 인증 요청
            Authentication authenticationResult =
                    authenticationManager.authenticate(
                            authenticationRequest
                    );

            // 인증된 사용자의 권한 가져오기
            String role = authenticationResult
                    .getAuthorities()
                    .stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ROLE_USER");

            // 인증된 사용자 정보로 Access Token 생성
            String accessToken =
                    jwtTokenProvider.createAccessToken(
                            authenticationResult.getName(),
                            role
                    );

            // Access Token 재발급용 Refresh Token 생성
            String refreshToken =
                    jwtTokenProvider.createRefreshToken(
                            authenticationResult.getName()
                    );

            // Refresh Token을 Redis에 저장
            refreshTokenService.save(
                    authenticationResult.getName(),
                    refreshToken
            );

            // React에 보낼 JSON 응답
            LoginResponse response = new LoginResponse(
                    1,
                    "로그인에 성공했습니다.",
                    accessToken
            );

            // Controller에 JSON 응답과 Refresh Token 전달
            return new LoginResult(
                    response,
                    refreshToken
            );

        } catch (AuthenticationException e) {
            throw new LoginFailedException(
                    "이메일 또는 비밀번호가 일치하지 않습니다."
            );
        }
    }


}