package com.pulse.api;

import com.pulse.api.dto.EmailCodeSendRequest;
import com.pulse.api.dto.EmailCodeSendResponse;
import com.pulse.api.dto.EmailCodeVerifyRequest;
import com.pulse.api.dto.EmailCodeVerifyResponse;
import com.pulse.common.exception.DuplicateEmailException;
import com.pulse.common.exception.EmailVerificationException;
import com.pulse.common.mail.EmailSender;
import com.pulse.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    // 인증번호 유효시간
    private static final Duration CODE_TTL =    // TTL -> Time To Live의 약자
            Duration.ofMinutes(5);              // 5분 동안 살아있다는 뜻

    // 인증 완료 후 회원가입할 수 있는 유효시간
    private static final Duration VERIFIED_TTL =
            Duration.ofMinutes(30);             // 인증 완료 후 해당 인증 키가 하루종일 유지하면 안되므로 처리한 코드(30분 제한)

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();     // SecureRandom은 보안용 난수 생성기

    private final StringRedisTemplate stringRedisTemplate;
    private final MemberRepository memberRepository;
    private final EmailSender emailSender;

    /**
     * 이메일 인증번호 생성 및 Redis 저장
     */
    public EmailCodeSendResponse sendCode(
            EmailCodeSendRequest request
    ) { // 인증번호 받기 버튼 누르면 호출될 메서드
        String email = normalizeEmail(request.email());

        // 인증번호 요청 시에도 이메일 중복 여부를 다시 검사
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        String code = createVerificationCode(); // 인증번호 생성

        // Redis 키 생성
        String codeKey = createCodeKey(email);
        String verifiedKey = createVerifiedKey(email);

        // 이전 인증 완료 상태가 있다면 초기화
        stringRedisTemplate.delete(verifiedKey);

        // 인증번호를 Redis에 5분 동안 저장
        stringRedisTemplate.opsForValue()   // opsForValue -> 문자열 하나를 값으로 저장하는 기능을 사용한다는 뜻
                .set(codeKey, code, CODE_TTL);  // (저장할 키, 저장할 값, 만료시간)

        try {
            emailSender.sendVerificationCode(email, code);
        } catch (MailException exception) {

            // 메일 발송에 실패했다면 사용할 수 없는 Redis 인증번호도 삭제
            stringRedisTemplate.delete(codeKey);

            throw new EmailVerificationException(
                    "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return new EmailCodeSendResponse(
                1,
                "인증번호를 발급했습니다."
        );
    }

    /**
     * 사용자가 입력한 인증번호 확인
     */
    public EmailCodeVerifyResponse verifyCode(
            EmailCodeVerifyRequest request
    ) {
        String email = normalizeEmail(request.email());
        String codeKey = createCodeKey(email);

        // Redis에서 저장된 인증번호 가져오기
        String savedCode =
                stringRedisTemplate.opsForValue().get(codeKey);

        // Redis에 값이 없다면 만료됐거나 발급받지 않은 상태
        if (savedCode == null) {
            throw new EmailVerificationException(
                    "인증번호가 만료되었거나 존재하지 않습니다."
            );
        }

        if (!savedCode.equals(request.code())) {
            throw new EmailVerificationException(
                    "인증번호가 일치하지 않습니다."
            );
        }

        // 사용한 인증번호 삭제
        stringRedisTemplate.delete(codeKey);

        // 해당 이메일이 인증됐다는 기록을 30분간 저장
        stringRedisTemplate.opsForValue().set(
                createVerifiedKey(email),
                "true",
                VERIFIED_TTL
        );

        return new EmailCodeVerifyResponse(
                true,
                "이메일 인증이 완료되었습니다."
        );
    }

    /**
     * 회원가입 시 인증 완료 여부 확인
     */
    public boolean isVerified(String email) {
        String value = stringRedisTemplate.opsForValue().get(
                createVerifiedKey(normalizeEmail(email))
        );

        return "true".equals(value);
    }

    /**
     * 회원가입 성공 후 인증 완료 기록 삭제
     */
    public void removeVerifiedEmail(String email) {
        stringRedisTemplate.delete(
                createVerifiedKey(normalizeEmail(email))
        );
    }

    private String createVerificationCode() {
        int number =
                SECURE_RANDOM.nextInt(900_000) + 100_000;

        return String.valueOf(number);
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String createCodeKey(String email) {
        return "email-verification:code:" + email;
    }

    private String createVerifiedKey(String email) {
        return "email-verification:verified:" + email;
    }
}