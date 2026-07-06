package com.pulse.api.user.dto;

public record EmailCodeSendResponse(
        int result,
        String message
) {
}