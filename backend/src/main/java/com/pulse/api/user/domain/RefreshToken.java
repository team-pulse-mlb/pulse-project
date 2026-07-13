package com.pulse.api.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DB에 저장하는 Refresh Token 정보
 *
 * 중요:
 * - Refresh Token 원문은 저장하지 않는다.
 * - SHA-256 해시값만 저장한다.
 * - revokedAt은 만료 전까지 보존해서 재사용 감지 근거로 사용한다.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    /**
     * 어떤 회원의 Refresh Token인지 연결
     *
     * 현재 Member 엔티티는 users 테이블을 사용하고,
     * PK 컬럼명이 user_id이므로 여기도 user_id로 연결한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member member;

    /**
     * Refresh Token 원문이 아니라 해시값만 저장한다.
     * SHA-256 hex 문자열은 64자다.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefreshTokenStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 폐기 시각
     *
     * refresh 성공으로 회전되거나,
     * 로그아웃되거나,
     * 재사용 감지로 폐기되면 값이 들어간다.
     *
     * 이 값이 있어야 “이미 폐기된 토큰이 다시 들어왔다”를 판단할 수 있다.
     */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    /**
     * 새 Refresh Token 저장용 정적 생성 메서드
     */
    public static RefreshToken create(
            Member member,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.member = member;
        refreshToken.tokenHash = tokenHash;
        refreshToken.status = RefreshTokenStatus.ACTIVE;
        refreshToken.expiresAt = expiresAt;
        refreshToken.createdAt = LocalDateTime.now();

        return refreshToken;
    }

    /**
     * 정상 폐기 처리
     */
    public void revoke(LocalDateTime revokedAt) {
        this.status = RefreshTokenStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    /**
     * 재사용 감지 처리
     */
    public void markReused(LocalDateTime reusedAt) {
        this.status = RefreshTokenStatus.REUSED;
        this.revokedAt = reusedAt;
    }

    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE
                && revokedAt == null
                && expiresAt.isAfter(LocalDateTime.now());
    }

    public boolean belongsTo(Member member) {
        return this.member.getUserId().equals(member.getUserId());
    }
}