package com.pulse.api.user.dto;

// 회원가입 버튼을 누르기 전에 사용하는 이메일 중복확인 API
// 실제 회원가입 시 MemberService.signup()의 중복 검사는 그대로 유지
// 중복확인 직후 다른 사용자가 같은 이메일로 가입할 수도 있기 때문
public record EmailCheckResponse(
        String code,
        boolean available,
        String message
) {

}
