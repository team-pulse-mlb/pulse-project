package com.pulse.api.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로그인한 사용자의 비밀번호 변경 요청 DTO입니다.
 *
 * 프론트 요청 예시:
 *
 * {
 *   "currentPassword": "현재 비밀번호",
 *   "newPassword": "새 비밀번호",
 *   "newPasswordConfirm": "새 비밀번호 확인"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
public class ChangePasswordRequest {

    /**
     * 사용자가 현재 사용 중인 비밀번호입니다.
     *
     * DB에 저장된 BCrypt 해시와
     * PasswordEncoder.matches()를 이용해 비교합니다.
     */
    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    private String currentPassword;

    /**
     * 새로 변경할 비밀번호입니다.
     *
     * 기존 회원가입 비밀번호 정책과 동일하게
     * 8자 이상 64자 이하로 검증합니다.
     */
    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(
            min = 8,
            max = 64,
            message = "새 비밀번호는 8자 이상 64자 이하로 입력해 주세요."
    )
    private String newPassword;

    /**
     * 새 비밀번호 확인 값입니다.
     *
     * newPassword와 같은지는 MemberService에서 검사합니다.
     * DTO에서 처리하지 않는 이유는 비밀번호 불일치에 맞는
     * 명확한 오류 코드와 메시지를 반환하기 위해서입니다.
     */
    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
    @Size(
            min = 8,
            max = 64,
            message = "새 비밀번호 확인은 8자 이상 64자 이하로 입력해 주세요."
    )
    private String newPasswordConfirm;
}