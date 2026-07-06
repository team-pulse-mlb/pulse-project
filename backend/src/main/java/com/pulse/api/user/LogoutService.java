package com.pulse.api.user;

import com.pulse.api.user.security.RefreshTokenService;
import com.pulse.api.user.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    /**
     * 로그아웃 처리
     * - Refresh Token이 정상이라면 Redis에서 삭제
     * - 토큰이 없거나 잘못되어도 로그아웃 응답 자체는 성공으로 처리
     */
    public void logout(String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            Claims claims =
                    jwtTokenProvider.parseClaims(refreshToken);

            String tokenType = claims.get(
                    "tokenType",
                    String.class
            );

            if (!"refresh".equals(tokenType)) {
                return;
            }

            String email = claims.getSubject()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            refreshTokenService.delete(email);

        } catch (JwtException | IllegalArgumentException exception) {
            // 이미 만료되었거나 잘못된 토큰이어도
            // 클라이언트 쿠키는 Controller에서 지울 예정이므로 무시
        }
    }
}