package com.pulse.api.user.dto;

public record EmailCodeVerifyResponse(
        boolean verified,
        String message
) {
}