package com.pulse.api.dto;

public record ErrorResponse(
        int result,
        String message
) {
}
