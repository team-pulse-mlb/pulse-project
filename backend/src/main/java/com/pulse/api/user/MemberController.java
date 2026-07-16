package com.pulse.api.user;

import com.pulse.api.user.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController // React 같은 클라이언트의 HTTP 요청을 처리하고, 데이터를 응답하는 컨트롤러 반환한 객체를 json으로 변환
                // RequestBody  + Cotroller
                // 반환값을 화면 이름으로 해석하지 말고 HTTP 응답 데이터로 보내라
@RequestMapping("api/members")  // 공통 주소
@RequiredArgsConstructor
@Slf4j
@Validated  // @RequestParam에 붙인 검증 어노테이션을 작동시키는 역할
@CrossOrigin(origins = "http://localhost:5173") // 임시 리엑트 연결 허용(로그인 창 미완성)
public class MemberController {

    private final MemberService memberService;
    private final EmailVerificationService emailVerificationService;

    // 회원가입 처리
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request
            // React가 HTTP 요청의 본문, 즉 body에 넣어서 보낸 JSON을 Java 객체로 바꿔주는 역할
            // @Valid가 있어야 SignupRequest의 @NotBlank, @Email, @Size가 실제로 검사
    ) {

        // 비밀번호는 로그에 출력하면 안 됨
        log.info("회원가입 요청 email={}", request.getEmail());

        SignupResponse response = memberService.signup(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
        // 200 OK
        // → 요청을 정상 처리함
        //
        // 201 Created
        // → 요청을 정상 처리했고 새로운 데이터를 생성함
    }


    // 이메일 인증번호 발급
    @PostMapping("/email/code/send")
    public ResponseEntity<EmailCodeSendResponse> sendEmailCode(
            @Valid @RequestBody EmailCodeSendRequest request
    ) {
        EmailCodeSendResponse response =
                emailVerificationService.sendCode(request);

        return ResponseEntity.ok(response);
    }


    // 이메일 인증번호 확인
    @PostMapping("/email/code/verify")
    public ResponseEntity<EmailCodeVerifyResponse> verifyEmailCode(
            @Valid @RequestBody EmailCodeVerifyRequest request
    ) {
        EmailCodeVerifyResponse response =
                emailVerificationService.verifyCode(request);

        return ResponseEntity.ok(response);
    }


    // 이메일 중복 처리
    @GetMapping("/email/check")
    public ResponseEntity<EmailCheckResponse> checkEmail(
            @RequestParam
            @NotBlank(message = "이메일을 입력해 주세요.")
            @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
            String email
    ) {
        EmailCheckResponse response =
                memberService.checkEmail(email);

        return ResponseEntity.ok(response);
    }


    /**
     * 로그인한 사용자의 비밀번호를 변경합니다.
     *
     * 요청 경로:
     * PUT /api/members/me/password
     *
     * Authentication.getName():
     * - JwtAuthenticationFilter가 등록한 로그인 사용자 이메일
     *
     * 처리 성공 시:
     * - 새 비밀번호가 BCrypt 해시로 저장됩니다.
     * - 해당 사용자의 활성 Refresh Token이 모두 폐기됩니다.
     * - 프론트에서는 Access Token을 삭제하고 다시 로그인시켜야 합니다.
     */
    @PutMapping("/me/password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        ChangePasswordResponse response =
                memberService.changePassword(
                        authentication.getName(),
                        request
                );

        return ResponseEntity.ok(response);
    }


}
