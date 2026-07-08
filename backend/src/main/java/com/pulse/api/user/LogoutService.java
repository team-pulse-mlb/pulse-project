package com.pulse.api.user;

import com.pulse.api.user.security.PersistentRefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutService {

    private final PersistentRefreshTokenService persistentRefreshTokenService;

    /**
     * 로그아웃 처리
     *
     * 기존 Redis 방식:
     * - refreshToken에서 email 추출
     * - Redis key 삭제
     *
     * 변경된 DB 방식:
     * - 클라이언트가 제시한 refreshToken 원문을 SHA-256 해시로 변환
     * - refresh_tokens 테이블에서 해당 해시 row 조회
     * - 아직 폐기되지 않은 토큰이면 revoked_at 기록
     *
     * 토큰이 없거나 잘못되어도 로그아웃 응답 자체는 성공으로 처리한다.
     * 이유:
     * - 클라이언트 쿠키 삭제가 로그아웃의 핵심이기 때문
     * - 이미 만료/삭제된 토큰 때문에 로그아웃을 실패시킬 필요는 없음
     */
    public void logout(String refreshToken) {
        persistentRefreshTokenService.revokePresentedToken(
                refreshToken
        );
    }
}