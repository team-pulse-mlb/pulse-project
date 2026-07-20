package com.pulse.api.user.dto;

/**
 * 비밀번호 변경 성공 응답 DTO입니다.
 *
 * 프로젝트의 다른 단순 응답 DTO와 동일하게
 * code와 message만 반환합니다.
 */
public record ChangePasswordResponse(
        String code,
        String message
) {
}