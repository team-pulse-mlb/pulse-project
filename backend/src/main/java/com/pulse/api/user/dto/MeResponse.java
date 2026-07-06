package com.pulse.api.user.dto;

import java.util.List;

public record MeResponse(
        int result,
        String email,
        List<String> roles
) {
}