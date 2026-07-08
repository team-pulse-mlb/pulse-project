package com.pulse.api.user.dto;

public record TokenRefreshResponse(
        String code,
        String message,
        String accessToken
) {
}