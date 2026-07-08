package com.pulse.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {    // 검증 규칙을 정의하는 부분

    // React에서 보내는 JSON을 여기 DTO가 받음

    // 프론트 검사를 우회하더라도 서버가 잘못된 이메일·비밀번호를 막도록 @Valid 검증을 추가
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(
            min = 8,
            max = 64,
            message = "비밀번호는 8자 이상 64자 이하로 입력해 주세요."
    )
    private String password;
}
