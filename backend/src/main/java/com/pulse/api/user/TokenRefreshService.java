package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.dto.TokenRefreshResponse;
import com.pulse.api.user.exception.InvalidRefreshTokenException;
import com.pulse.api.user.security.RefreshTokenService;
import com.pulse.api.user.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final MemberRepository memberRepository;

    /**
     * Refresh Token으로 새 Access Token 발급
     */
    public TokenRefreshResponse refreshAccessToken(
            String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 없습니다."
            );
        }

        Claims claims;

        try {
            claims = jwtTokenProvider.parseClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidRefreshTokenException(
                    "유효하지 않은 Refresh Token입니다."
            );
        }

        String tokenType = claims.get(
                "tokenType",
                String.class
        );

        if (!"refresh".equals(tokenType)) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 아닙니다."
            );
        }

        String email = claims.getSubject()
                .trim()
                .toLowerCase(Locale.ROOT);

        String savedRefreshToken =
                refreshTokenService.find(email);

        if (savedRefreshToken == null ||
                !savedRefreshToken.equals(refreshToken)) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 만료되었거나 일치하지 않습니다."
            );
        }

        // Refresh Token 안의 권한을 믿지 않고 DB에서 현재 회원 권한을 다시 조회
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException(
                                "회원을 찾을 수 없습니다."
                        )
                );

        String role = "ROLE_" + member.getRole().name();

        String newAccessToken =
                jwtTokenProvider.createAccessToken(
                        email,
                        role
                );

        return new TokenRefreshResponse(
                1,
                "Access Token을 재발급했습니다.",
                newAccessToken
        );
    }
}