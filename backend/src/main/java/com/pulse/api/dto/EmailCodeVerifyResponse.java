package com.pulse.api.dto;

public record EmailCodeVerifyResponse(
        boolean verified,
        String message
) {
}