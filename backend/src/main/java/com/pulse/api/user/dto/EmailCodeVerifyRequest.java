package com.pulse.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailCodeVerifyRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "인증번호 6자리를 입력해 주세요."
        )
        String code
) {
}