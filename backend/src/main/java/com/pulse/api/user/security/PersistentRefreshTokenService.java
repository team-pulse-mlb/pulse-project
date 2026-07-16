package com.pulse.api.user.security;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.RefreshToken;
import com.pulse.api.user.domain.RefreshTokenRepository;
import com.pulse.api.user.domain.RefreshTokenStatus;
import com.pulse.api.user.exception.InvalidRefreshTokenException;
import com.pulse.api.user.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * DB 기반 Refresh Token 관리 서비스
 *
 * 역할:
 * - 로그인 시 refreshToken 해시 저장
 * - refresh 성공 시 기존 토큰 폐기 후 새 토큰 저장
 * - 폐기된 토큰 재사용 감지
 * - 재사용 감지 시 해당 사용자 활성 토큰 전체 폐기
 * - 로그아웃 시 제시된 refreshToken 폐기
 */
@Service
@RequiredArgsConstructor
public class PersistentRefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenHashService refreshTokenHashService;
    private final JwtProperties jwtProperties;

    /**
     * 로그인 성공 시 새 Refresh Token 해시 저장
     *
     * 여기서 기존 토큰을 지우지 않는다.
     * 이유:
     * - 기기 2대 로그인 허용을 위해
     * - 사용자별 최신 토큰 1개만 두면 새 로그인 시 기존 기기가 강제 로그아웃되기 때문
     */
    @Transactional
    public void saveNewToken(
            Member member,
            String rawRefreshToken
    ) {
        String tokenHash =
                refreshTokenHashService.hash(rawRefreshToken);

        LocalDateTime expiresAt =
                LocalDateTime.now().plus(
                        jwtProperties.refreshTokenExpiration()
                );

        RefreshToken refreshToken =
                RefreshToken.create(
                        member,
                        tokenHash,
                        expiresAt
                );

        refreshTokenRepository.save(refreshToken);
    }

    /**
     * refresh 요청 성공 시 토큰 회전 처리
     *
     * oldRawRefreshToken
     * - 클라이언트 쿠키로 들어온 기존 Refresh Token
     *
     * newRawRefreshToken
     * - 서버가 새로 발급한 Refresh Token
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean rotate(
            Member member,
            String oldRawRefreshToken,
            String newRawRefreshToken
    ) {
        LocalDateTime now = LocalDateTime.now();

        String oldTokenHash =
                refreshTokenHashService.hash(oldRawRefreshToken);

        RefreshToken savedToken =
                refreshTokenRepository.findByTokenHash(oldTokenHash)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Refresh Token을 찾을 수 없습니다."
                                )
                        );

        /**
         * 토큰 해시는 DB에 있지만,
         * 요청한 사용자와 DB row의 사용자가 다르면 비정상 상황이다.
         */
        if (!savedToken.belongsTo(member)) {
            revokeAllActiveTokens(member, now);
            return false;
        }

        /**
         * 핵심: 재사용 감지
         *
         * 이미 revoke된 Refresh Token이 다시 들어왔다면
         * 탈취된 토큰 재사용 가능성이 있다.
         *
         * 이 경우 현재 사용자의 활성 Refresh Token을 모두 폐기한다.
         */
        if (!savedToken.isActive()) {
            // 이미 폐기된 Refresh Token이 다시 들어온 상황
            // 탈취된 토큰 재사용 가능성이 있으므로 이 토큰을 REUSED로 표시한다.
            savedToken.markReused(now);

            // bulk update 전에 REUSED 상태를 먼저 DB에 반영한다.
            refreshTokenRepository.saveAndFlush(savedToken);

            // 같은 사용자의 현재 ACTIVE Refresh Token도 전부 폐기한다.
            revokeAllActiveTokens(member, now);

            return false;
        }

        /**
         * 정상 refresh:
         * 기존 토큰을 폐기하고 새 토큰 해시를 저장한다.
         */
        savedToken.revoke(now);

        saveNewToken(
                member,
                newRawRefreshToken
        );

        return true;
    }

    /**
     * 로그아웃 시 제시된 Refresh Token 폐기
     *
     * 이미 만료되었거나 DB에 없는 토큰이면 조용히 무시한다.
     * Controller에서 쿠키는 어차피 삭제한다.
     *
     * 주의:
     * 여기서는 JWT 검증을 다시 하지 않고 해시값으로 DB row만 찾는다.
     * 로그아웃은 "서버에 저장된 해당 토큰을 폐기하고 쿠키를 지우는 행위"이기 때문이다.
     */
    @Transactional
    public void revokePresentedToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash =
                refreshTokenHashService.hash(rawRefreshToken);

        refreshTokenRepository.findByTokenHash(tokenHash)
                // 이미 폐기된 토큰이면 다시 처리하지 않는다.
                .filter(refreshToken -> refreshToken.getRevokedAt() == null)
                .ifPresent(refreshToken ->
                        refreshToken.revoke(LocalDateTime.now())
                );
    }

    /**
     * 계정 보안 상태가 변경된 사용자의
     * 활성 Refresh Token을 모두 폐기합니다.
     *
     * 사용 예:
     * - 비밀번호 변경
     * - 회원탈퇴
     * - 계정 보안 사고 대응
     *
     * 모든 기기의 Refresh Token을 폐기하므로,
     * 이후에는 기존 Refresh Token으로 Access Token을
     * 다시 발급받을 수 없습니다.
     */
    @Transactional
    public void revokeAllActiveTokens(Member member) {
        if (member == null || member.getUserId() == null) {
            throw new IllegalArgumentException(
                    "Refresh Token을 폐기할 회원 정보가 올바르지 않습니다."
            );
        }

        revokeAllActiveTokens(
                member,
                LocalDateTime.now()
        );
    }

    /**
     * 특정 사용자의 활성 Refresh Token 전체 폐기
     *
     * 폐기 시각을 직접 전달해야 하는 내부 처리에서 사용합니다.
     *
     * 예:
     * - Refresh Token 재사용 감지 과정에서
     *   동일한 시각으로 여러 토큰을 폐기할 때 사용
     */
    private void revokeAllActiveTokens(
            Member member,
            LocalDateTime revokedAt
    ) {
        refreshTokenRepository.updateAllActiveTokensStatus(
                member.getUserId(),
                RefreshTokenStatus.ACTIVE,
                RefreshTokenStatus.REVOKED,
                revokedAt
        );
    }
}
