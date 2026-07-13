package com.pulse.api.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * refreshToken 원문을 해시로 바꾼 뒤 해당 해시로 DB row를 찾는다.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 특정 사용자의 활성 Refresh Token 전체 폐기
     *
     * 재사용 감지 시 해당 사용자의 다른 기기 토큰까지 모두 무효화하기 위해 사용한다.
     */
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
            update RefreshToken rt
            set rt.status = :newStatus,
                rt.revokedAt = :revokedAt
            where rt.member.userId = :userId
              and rt.status = :currentStatus
              and rt.revokedAt is null
            """)
    int updateAllActiveTokensStatus(
            Long userId,
            RefreshTokenStatus currentStatus,
            RefreshTokenStatus newStatus,
            LocalDateTime revokedAt
    );
}