package com.pulse.api.user.dto;

public record ErrorResponse(
        int result,
        String message
) {
}
