package com.pulse.api.user.dto;

public record TokenRefreshResponse(
        int result,
        String message,
        String accessToken
) {
}