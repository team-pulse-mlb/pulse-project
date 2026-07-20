package com.pulse.api.user;

import com.pulse.api.user.dto.EmailCodeSendRequest;
import com.pulse.api.user.dto.EmailCodeSendResponse;
import com.pulse.api.user.dto.EmailCodeVerifyRequest;
import com.pulse.api.user.dto.EmailCodeVerifyResponse;
import com.pulse.api.user.exception.DuplicateEmailException;
import com.pulse.api.user.exception.EmailVerificationException;
import com.pulse.api.user.mail.EmailSender;
import com.pulse.api.user.domain.MemberRepository;
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

    /**
     * 회원가입 이메일 인증 완료 상태의 유효시간입니다.
     *
     * 인증 완료 후 30분 안에 회원가입해야 합니다.
     */
    private static final Duration SIGNUP_VERIFIED_TTL =
            Duration.ofMinutes(30);

    /**
     * 비밀번호 변경 이메일 인증 완료 상태의 유효시간입니다.
     *
     * 비밀번호 변경은 민감한 작업이므로
     * 회원가입보다 짧은 10분만 허용합니다.
     */
    private static final Duration PASSWORD_CHANGE_VERIFIED_TTL =
            Duration.ofMinutes(10);

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();     // SecureRandom은 보안용 난수 생성기

    private final StringRedisTemplate stringRedisTemplate;
    private final MemberRepository memberRepository;
    private final EmailSender emailSender;

    /**
     * 회원가입용 이메일 인증번호를 발급합니다.
     *
     * 기존 회원가입 Controller와의 호환성을 유지하기 위해
     * 기존 메서드 이름과 요청 DTO를 그대로 사용합니다.
     */
    public EmailCodeSendResponse sendCode(
            EmailCodeSendRequest request
    ) {
        return sendCode(
                request.email(),
                EmailVerificationPurpose.SIGNUP
        );
    }

    /**
     * 로그인한 사용자의 비밀번호 변경용
     * 이메일 인증번호를 발급합니다.
     *
     * 이메일은 프론트 요청에서 받지 않고,
     * 이후 Controller에서 Authentication.getName()으로 전달합니다.
     */
    public EmailCodeSendResponse sendPasswordChangeCode(
            String email
    ) {
        return sendCode(
                email,
                EmailVerificationPurpose.PASSWORD_CHANGE
        );
    }

    /**
     * 인증 목적에 따라 이메일 인증번호를 생성하고 Redis에 저장합니다.
     */
    private EmailCodeSendResponse sendCode(
            String rawEmail,
            EmailVerificationPurpose purpose
    ) {
        String email = normalizeEmail(rawEmail);

        /*
         * 회원가입과 비밀번호 변경은
         * 이메일 존재 여부 검증 규칙이 서로 다릅니다.
         */
        validateEmailForPurpose(email, purpose);

        String code = createVerificationCode();

        /*
         * 인증 목적을 Redis 키에 포함해
         * 회원가입 인증과 비밀번호 변경 인증이 섞이지 않게 합니다.
         */
        String codeKey = createCodeKey(email, purpose);
        String verifiedKey = createVerifiedKey(email, purpose);

        /*
         * 새 인증번호를 발급하면 같은 목적의
         * 기존 인증 완료 상태는 초기화합니다.
         */
        stringRedisTemplate.delete(verifiedKey);

        stringRedisTemplate.opsForValue()
                .set(
                        codeKey,
                        code,
                        CODE_TTL
                );

        try {
            emailSender.sendVerificationCode(
                    email,
                    code
            );
        } catch (MailException exception) {

            /*
             * 메일 발송이 실패한 인증번호는
             * 실제 사용이 불가능하므로 Redis에서도 삭제합니다.
             */
            stringRedisTemplate.delete(codeKey);

            throw new EmailVerificationException(
                    "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return new EmailCodeSendResponse(
                "SUCCESS",
                "인증번호를 발급했습니다."
        );
    }

    /**
     * 회원가입용 이메일 인증번호를 확인합니다.
     *
     * 기존 회원가입 Controller와의 호환성을 위해
     * 기존 요청 DTO를 그대로 사용합니다.
     */
    public EmailCodeVerifyResponse verifyCode(
            EmailCodeVerifyRequest request
    ) {
        return verifyCode(
                request.email(),
                request.code(),
                EmailVerificationPurpose.SIGNUP
        );
    }

    /**
     * 비밀번호 변경용 이메일 인증번호를 확인합니다.
     *
     * 이메일은 로그인한 사용자의 Authentication에서 가져오고,
     * 사용자가 입력한 인증번호만 별도로 전달합니다.
     */
    public EmailCodeVerifyResponse verifyPasswordChangeCode(
            String email,
            String code
    ) {
        return verifyCode(
                email,
                code,
                EmailVerificationPurpose.PASSWORD_CHANGE
        );
    }

    /**
     * 인증 목적에 맞는 Redis 인증번호를 확인합니다.
     */
    private EmailCodeVerifyResponse verifyCode(
            String rawEmail,
            String code,
            EmailVerificationPurpose purpose
    ) {
        String email = normalizeEmail(rawEmail);

        if (code == null || code.isBlank()) {
            throw new EmailVerificationException(
                    "인증번호를 입력해 주세요."
            );
        }

        String codeKey = createCodeKey(
                email,
                purpose
        );

        String savedCode =
                stringRedisTemplate
                        .opsForValue()
                        .get(codeKey);

        if (savedCode == null) {
            throw new EmailVerificationException(
                    "인증번호가 만료되었거나 존재하지 않습니다."
            );
        }

        if (!savedCode.equals(code)) {
            throw new EmailVerificationException(
                    "인증번호가 일치하지 않습니다."
            );
        }

        /*
         * 한 번 사용한 인증번호는 즉시 삭제해
         * 다시 사용할 수 없게 합니다.
         */
        stringRedisTemplate.delete(codeKey);

        /*
         * 인증 목적에 따라 인증 완료 상태의 TTL을 다르게 적용합니다.
         */
        stringRedisTemplate.opsForValue().set(
                createVerifiedKey(email, purpose),
                "true",
                getVerifiedTtl(purpose)
        );

        return new EmailCodeVerifyResponse(
                "SUCCESS",
                true,
                "이메일 인증이 완료되었습니다."
        );
    }

    /**
     * 회원가입 이메일 인증 완료 여부를 확인합니다.
     *
     * 기존 MemberService.signup()과의 호환성을 유지합니다.
     */
    public boolean isVerified(String email) {
        return isVerified(
                email,
                EmailVerificationPurpose.SIGNUP
        );
    }

    /**
     * 비밀번호 변경 이메일 인증 완료 여부를 확인합니다.
     */
    public boolean isPasswordChangeVerified(String email) {
        return isVerified(
                email,
                EmailVerificationPurpose.PASSWORD_CHANGE
        );
    }

    /**
     * 해당 목적의 이메일 인증 완료 여부를 Redis에서 확인합니다.
     */
    private boolean isVerified(
            String email,
            EmailVerificationPurpose purpose
    ) {
        String value =
                stringRedisTemplate
                        .opsForValue()
                        .get(
                                createVerifiedKey(
                                        normalizeEmail(email),
                                        purpose
                                )
                        );

        return "true".equals(value);
    }

    /**
     * 회원가입 성공 후 회원가입용 인증 완료 기록을 삭제합니다.
     *
     * 기존 MemberService.signup()과의 호환성을 유지합니다.
     */
    public void removeVerifiedEmail(String email) {
        removeVerifiedEmail(
                email,
                EmailVerificationPurpose.SIGNUP
        );
    }

    /**
     * 비밀번호 변경 성공 후
     * 비밀번호 변경용 인증 완료 기록을 삭제합니다.
     */
    public void removePasswordChangeVerification(String email) {
        removeVerifiedEmail(
                email,
                EmailVerificationPurpose.PASSWORD_CHANGE
        );
    }

    /**
     * 해당 목적의 인증 완료 기록을 Redis에서 삭제합니다.
     */
    private void removeVerifiedEmail(
            String email,
            EmailVerificationPurpose purpose
    ) {
        stringRedisTemplate.delete(
                createVerifiedKey(
                        normalizeEmail(email),
                        purpose
                )
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

    /**
     * 인증 목적에 맞게 이메일 존재 여부를 검사합니다.
     */
    private void validateEmailForPurpose(
            String email,
            EmailVerificationPurpose purpose
    ) {
        boolean memberExists =
                memberRepository.existsByEmail(email);

        switch (purpose) {
            case SIGNUP -> {
                /*
                 * 회원가입 인증은 아직 가입되지 않은
                 * 이메일에만 발급할 수 있습니다.
                 */
                if (memberExists) {
                    throw new DuplicateEmailException(
                            "이미 사용 중인 이메일입니다."
                    );
                }
            }

            case PASSWORD_CHANGE -> {
                /*
                 * 비밀번호 변경 인증은 이미 가입된
                 * 로그인 사용자에게만 발급할 수 있습니다.
                 */
                if (!memberExists) {
                    throw new EmailVerificationException(
                            "회원 정보를 확인할 수 없습니다."
                    );
                }
            }
        }
    }

    /**
     * 인증 목적에 따른 인증 완료 상태의 TTL을 반환합니다.
     */
    private Duration getVerifiedTtl(
            EmailVerificationPurpose purpose
    ) {
        return switch (purpose) {
            case SIGNUP ->
                    SIGNUP_VERIFIED_TTL;

            case PASSWORD_CHANGE ->
                    PASSWORD_CHANGE_VERIFIED_TTL;
        };
    }

    /**
     * 인증번호가 저장되는 Redis 키를 생성합니다.
     */
    private String createCodeKey(
            String email,
            EmailVerificationPurpose purpose
    ) {
        return "email-verification:code:"
                + purpose.name()
                + ":"
                + email;
    }

    /**
     * 인증 완료 상태가 저장되는 Redis 키를 생성합니다.
     */
    private String createVerifiedKey(
            String email,
            EmailVerificationPurpose purpose
    ) {
        return "email-verification:verified:"
                + purpose.name()
                + ":"
                + email;
    }
}