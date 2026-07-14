package com.pulse.api.user.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailSender {

    private final JavaMailSender mailSender;

    /**
     * 실제 메일의 발신 주소입니다.
     *
     * MAIL_FROM이 설정되어 있으면 해당 주소를 사용하고,
     * 없으면 SMTP 로그인 사용자명인 spring.mail.username을 사용합니다.
     *
     * 로컬 Gmail:
     * MAIL_USERNAME과 MAIL_FROM이 같은 Gmail 주소
     *
     * 운영 AWS SES:
     * MAIL_USERNAME은 SES SMTP 사용자명
     * MAIL_FROM은 no-reply@pulsemlb.com
     */
    @Value("${MAIL_FROM:${spring.mail.username}}")
    private String fromAddress;

    public void sendVerificationCode(
            String recipientEmail,
            String verificationCode
    ) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject("[PULSE] 이메일 인증번호 안내");
        message.setText(
                """
                PULSE 회원가입 인증번호입니다.

                인증번호: %s

                인증번호는 5분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(verificationCode)
        );

        mailSender.send(message);
    }
}