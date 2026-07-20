package com.pulse.api.user.dto;

/**
 * 회원탈퇴 성공 응답 DTO입니다.
 */
public record WithdrawMemberResponse(
        String code,
        String message
) {
}