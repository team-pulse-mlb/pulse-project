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
     *
     * Refresh Token Rotation 적용:
     * 1. 클라이언트가 보낸 Refresh Token을 검증한다.
     * 2. Redis에 저장된 최신 Refresh Token과 일치하는지 확인한다.
     * 3. 새 Access Token을 발급한다.
     * 4. 새 Refresh Token도 발급한다.
     * 5. Redis의 Refresh Token을 새 값으로 교체한다.
     *
     * 이렇게 하면 기존 Refresh Token은 한 번 사용된 뒤 더 이상 유효하지 않게 된다.
     */
    public TokenRefreshResult refreshAccessToken(
            String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 없습니다."
            );
        }

        Claims claims;

        try {
            // JWT 서명, 만료시간 등을 검증하고 payload를 꺼낸다.
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

        // Access Token을 Refresh API에 보내는 실수를 막기 위한 검사
        if (!"refresh".equals(tokenType)) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 아닙니다."
            );
        }

        String email = claims.getSubject()
                .trim()
                .toLowerCase(Locale.ROOT);

        // Redis에 저장된 Refresh Token 조회
        String savedRefreshToken =
                refreshTokenService.find(email);

        // Rotation 보안 핵심:
        // JWT 자체가 유효하더라도 Redis에 저장된 최신 토큰과 다르면 거부한다.
        //
        // 예를 들어 예전 Refresh Token이 탈취되어 재사용되면
        // Redis에는 이미 새 Refresh Token이 저장되어 있으므로 여기서 막힌다.
        if (savedRefreshToken == null ||
                !savedRefreshToken.equals(refreshToken)) {
            throw new InvalidRefreshTokenException(
                    "Refresh Token이 만료되었거나 일치하지 않습니다."
            );
        }

        // Refresh Token 안의 권한 정보는 믿지 않고
        // DB에서 현재 회원 권한을 다시 조회한다.
        //
        // 이유:
        // 사용자의 권한이 중간에 USER에서 ADMIN으로 바뀌거나,
        // 정지/탈퇴 처리될 수도 있기 때문이다.
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() ->
                        new InvalidRefreshTokenException(
                                "회원을 찾을 수 없습니다."
                        )
                );

        String role = "ROLE_" + member.getRole().name();

        // 새 Access Token 발급
        String newAccessToken =
                jwtTokenProvider.createAccessToken(
                        email,
                        role
                );

        // 새 Refresh Token 발급
        //
        // Refresh Token Rotation 핵심:
        // refresh 요청이 성공할 때마다 Refresh Token도 새로 만든다.
        String newRefreshToken =
                jwtTokenProvider.createRefreshToken(
                        email
                );

        // React에 JSON으로 내려줄 응답
        // refreshToken은 JSON에 넣지 않고 Cookie로만 내려보낸다.
        TokenRefreshResponse response =
                new TokenRefreshResponse(
                        1,
                        "Access Token을 재발급했습니다.",
                        newAccessToken
                );

        // Controller에는 JSON 응답과 새 Refresh Token을 같이 전달한다.
        return new TokenRefreshResult(
                response,
                newRefreshToken
        );
    }
}