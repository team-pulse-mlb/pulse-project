package com.pulse.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 비밀번호 변경용 이메일 인증번호 확인 요청 DTO입니다.
 *
 * 회원가입 인증과 달리 이메일 필드를 받지 않습니다.
 *
 * 비밀번호 변경은 로그인한 사용자만 요청할 수 있으므로
 * 서버가 JWT Authentication에서 현재 사용자의 이메일을 가져옵니다.
 *
 * 요청 예시:
 *
 * {
 *   "code": "123456"
 * }
 */
public record PasswordChangeEmailCodeVerifyRequest(

        /**
         * 이메일로 전송된 6자리 인증번호입니다.
         */
        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "인증번호 6자리를 입력해 주세요."
        )
        String code
) {
}