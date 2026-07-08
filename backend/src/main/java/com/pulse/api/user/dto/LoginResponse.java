package com.pulse.api.user.dto;

public record LoginResponse(
        String code,
        String message,
        String accessToken
) {
}