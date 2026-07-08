package com.pulse.api.user.dto;

public record LogoutResponse(
        String code,
        String message
) {
}