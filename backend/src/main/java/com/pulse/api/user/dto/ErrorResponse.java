package com.pulse.api.user.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
