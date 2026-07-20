package com.pulse.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원탈퇴 요청 DTO입니다.
 *
 * 요청 예시:
 *
 * {
 *   "currentPassword": "현재 비밀번호",
 *   "confirmation": "회원탈퇴"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
public class WithdrawMemberRequest {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    @NotBlank(message = "회원탈퇴 확인 문구를 입력해 주세요.")
    private String confirmation;
}