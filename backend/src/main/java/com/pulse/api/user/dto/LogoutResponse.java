package com.pulse.api.user.dto;

public record LogoutResponse(
        int result,
        String message
) {
}