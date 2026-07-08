package com.pulse.api.user.domain;

/**
 * Refresh Token의 상태
 *
 * ACTIVE
 * - 현재 사용 가능한 Refresh Token
 *
 * REVOKED
 * - refresh 성공, 로그아웃 등으로 정상 폐기된 토큰
 *
 * REUSED
 * - 이미 폐기된 토큰이 다시 들어와 재사용 감지된 토큰
 */
public enum RefreshTokenStatus {
    ACTIVE,
    REVOKED,
    REUSED
}