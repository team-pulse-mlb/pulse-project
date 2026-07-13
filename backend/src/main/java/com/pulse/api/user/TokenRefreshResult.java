package com.pulse.api.user;

import com.pulse.api.user.dto.TokenRefreshResponse;

/**
 * Refresh Token 재발급 처리 결과
 *
 * response
 * - React에 JSON으로 내려줄 응답 데이터
 * - accessToken만 포함한다.
 *
 * refreshToken
 * - 새로 발급한 Refresh Token
 * - JSON 응답으로 직접 내려보내지 않는다.
 * - Controller에서 HttpOnly Cookie로 내려보낼 때 사용한다.
 */
public record TokenRefreshResult(
        TokenRefreshResponse response,
        String refreshToken
) {
}