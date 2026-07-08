package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.dto.TokenRefreshResponse;
import com.pulse.api.user.exception.InvalidRefreshTokenException;
import com.pulse.api.user.security.PersistentRefreshTokenService;
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
    private final PersistentRefreshTokenService persistentRefreshTokenService;
    private final MemberRepository memberRepository;

    /**
     * Refresh Token으로 새 Access Token 발급
     *
     * DB 기반 Refresh Token Rotation + 재사용 감지 적용:
     *
     * 1. 클라이언트 쿠키의 Refresh Token을 JWT로 검증한다.
     * 2. tokenType이 refresh인지 확인한다.
     * 3. subject(email)로 현재 회원을 조회한다.
     * 4. 기존 Refresh Token의 해시가 DB refresh_tokens에 ACTIVE 상태인지 확인한다.
     * 5. ACTIVE면 기존 토큰을 revoked_at 처리한다.
     * 6. 새 Access Token과 새 Refresh Token을 발급한다.
     * 7. 새 Refresh Token 해시를 DB에 저장한다.
     *
     * 이미 revoked_at이 있는 Refresh Token이 다시 들어오면
     * 토큰 탈취 가능성으로 보고 해당 사용자의 활성 Refresh Token을 모두 폐기한다.
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

        // Refresh Token 안의 권한 정보는 믿지 않고
        // DB에서 현재 회원 권한을 다시 조회한다.
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
        // Refresh Token Rotation:
        // refresh 요청이 성공할 때마다 새 Refresh Token으로 교체한다.
        String newRefreshToken =
                jwtTokenProvider.createRefreshToken(
                        email
                );

        // DB 기반 Rotation + 재사용 감지 처리
        //
        // 내부에서 하는 일:
        // - 기존 refreshToken 해시 조회
        // - ACTIVE인지 확인
        // - 이미 폐기된 토큰이면 재사용 감지 처리
        // - 정상이라면 기존 토큰 revoked_at 기록
        // - 새 refreshToken 해시 저장
        boolean rotated = persistentRefreshTokenService.rotate(
                member,
                refreshToken,
                newRefreshToken
        );

        if (!rotated) {
            throw new InvalidRefreshTokenException(
                    "폐기된 Refresh Token이 재사용되었습니다."
            );
        }

        // React에 JSON으로 내려줄 응답
        // refreshToken은 JSON에 넣지 않고 Cookie로만 내려보낸다.
        TokenRefreshResponse response =
                new TokenRefreshResponse(
                        "SUCCESS",
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