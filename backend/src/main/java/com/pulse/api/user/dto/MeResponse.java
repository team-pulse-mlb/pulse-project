package com.pulse.api.user.dto;

import java.util.List;

public record MeResponse(
        String code,
        String email,
        List<String> roles
) {
}