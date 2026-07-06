package com.pulse.api.user;

import com.pulse.api.user.dto.EmailCheckResponse;
import com.pulse.api.user.dto.SignupRequest;
import com.pulse.api.user.dto.SignupResponse;
import com.pulse.api.user.exception.DuplicateEmailException;
import com.pulse.api.user.exception.EmailVerificationException;
import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    // 회원 가입
    @Transactional
    public SignupResponse signup(SignupRequest request) {

        // 이메일 앞뒤 공백 제거 및 소문자 통일
        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);  // 실행 환경의 언어 설정과 상관없이 일관되게 소문자로 변경

        // 이메일 중복 검사
        if (memberRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(
                    "이미 사용 중인 이메일입니다."
            );
        }

        // Redis에서 실제 이메일 인증 완료 여부 검사
        if (!emailVerificationService.isVerified(email)) {
            throw new EmailVerificationException(
                    "이메일 인증을 완료해 주세요."
            );
        }

        // 비밀번호 암호화 후 Member 생성
        Member member = Member.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();

        // PostgreSQL에 회원 저장
        memberRepository.save(member);

        // PostgreSQL 트랜잭션이 실제로 커밋된 다음
        // Redis의 이메일 인증 완료 기록 삭제
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        emailVerificationService.removeVerifiedEmail(email);
                    }
                }
        );

        return new SignupResponse(
                1,
                "회원가입이 완료되었습니다."
        );
    }

    // 이메일 중복 체크
    public EmailCheckResponse checkEmail(String email) {

        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        boolean available =
                !memberRepository.existsByEmail(normalizedEmail);

        if (available) {
            return new EmailCheckResponse(
                    true,
                    "사용 가능한 이메일입니다."
            );
        }

        return new EmailCheckResponse(
                false,
                "이미 사용 중인 이메일입니다."
        );
    }




}
