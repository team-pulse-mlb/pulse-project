package com.pulse.api.user.dto;

public record EmailCodeVerifyResponse(
        String code,
        boolean verified,
        String message
) {
}