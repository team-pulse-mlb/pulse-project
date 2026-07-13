package com.pulse.api.sse.dto;

/**
 * SSE 인증 연결에 사용할 1회용 토큰 응답입니다.
 *
 * 발급된 토큰은 Redis에 60초 동안 저장되며,
 * GET /api/sse?token=... 요청에서 한 번 사용하면 삭제됩니다.
 *
 * @param token SSE 연결에 사용할 1회용 토큰
 */
public record SseTokenResponse(
        String token
) {
}