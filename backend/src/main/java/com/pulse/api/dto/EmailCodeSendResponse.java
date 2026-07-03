package com.pulse.api.dto;

public record EmailCodeSendResponse(
        int result,
        String message
) {
}