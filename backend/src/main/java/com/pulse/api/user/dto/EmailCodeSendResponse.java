package com.pulse.api.user.dto;

public record EmailCodeSendResponse(
        String code,
        String message
) {
}