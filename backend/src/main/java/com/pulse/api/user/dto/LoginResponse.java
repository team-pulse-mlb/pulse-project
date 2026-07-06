package com.pulse.api.user.dto;

public record LoginResponse(
        int result,
        String message,
        String accessToken
) {
}