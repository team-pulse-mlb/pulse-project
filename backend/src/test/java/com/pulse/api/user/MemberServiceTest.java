package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.UserFavoriteTeamRepository;
import com.pulse.api.user.domain.UserSettingRepository;
import com.pulse.api.user.dto.ChangePasswordRequest;
import com.pulse.api.user.dto.ChangePasswordResponse;
import com.pulse.api.user.exception.InvalidCurrentPasswordException;
import com.pulse.api.user.exception.PasswordMismatchException;
import com.pulse.api.user.exception.EmailVerificationException;
import com.pulse.api.user.exception.SamePasswordException;
import com.pulse.api.user.security.PersistentRefreshTokenService;
import com.pulse.domain.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MemberService의 계정 설정 기능 단위 테스트입니다.
 *
 * 실제 DB와 Spring Context는 실행하지 않고,
 * Repository와 보안 관련 서비스를 Mockito Mock으로 대체합니다.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    /*
     * MemberService의 회원가입 기능에 필요한 의존성입니다.
     *
     * 비밀번호 변경 테스트에서는 직접 사용하지 않지만,
     * @InjectMocks가 MemberService 생성자를 만들기 위해 필요합니다.
     */
    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private UserFavoriteTeamRepository userFavoriteTeamRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder
            passwordEncoder;

    @Mock
    private PersistentRefreshTokenService
            persistentRefreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    /**
     * 위 Mock 객체들을 생성자에 주입해
     * 실제 테스트 대상 MemberService를 생성합니다.
     */
    @InjectMocks
    private MemberService memberService;

    /**
     * 새 비밀번호와 확인 값이 다르면
     * 회원 조회나 비밀번호 검증을 진행하지 않고
     * 즉시 PasswordMismatchException을 발생시켜야 합니다.
     */
    @Test
    void changePassword_shouldRejectMismatchedNewPasswordConfirmation() {
        // given
        ChangePasswordRequest request = createRequest(
                "current-password",
                "new-password-123",
                "different-password-123"
        );

        // when
        PasswordMismatchException exception =
                assertThrows(
                        PasswordMismatchException.class,
                        () -> memberService.changePassword(
                                "user@example.com",
                                request
                        )
                );

        // then
        assertEquals(
                "새 비밀번호와 비밀번호 확인이 일치하지 않습니다.",
                exception.getMessage()
        );

        /*
         * 확인 값이 다르면 DB 조회나 BCrypt 검증까지
         * 진행해서는 안 됩니다.
         */
        verifyNoInteractions(
                memberRepository,
                passwordEncoder,
                persistentRefreshTokenService,
                emailVerificationService
        );
    }

    /**
     * 입력한 현재 비밀번호가 DB의 BCrypt 해시와 일치하지 않으면
     * InvalidCurrentPasswordException을 발생시켜야 합니다.
     */
    @Test
    void changePassword_shouldRejectIncorrectCurrentPassword() {
        // given
        String requestEmail = " USER@EXAMPLE.COM ";
        String normalizedEmail = "user@example.com";

        Member member = mock(Member.class);

        ChangePasswordRequest request = createRequest(
                "wrong-current-password",
                "new-password-123",
                "new-password-123"
        );

        /*
         * 이 테스트의 목적은 이메일 인증 실패가 아니라
         * 현재 비밀번호 불일치 검증입니다.
         *
         * 따라서 이메일 인증은 이미 완료된 상태로 설정합니다.
         */
        when(emailVerificationService
                .isPasswordChangeVerified(normalizedEmail))
                .thenReturn(true);

        when(memberRepository
                .findByEmailForUpdate(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(member.getPasswordHash())
                .thenReturn("stored-password-hash");

        when(passwordEncoder.matches(
                "wrong-current-password",
                "stored-password-hash"
        )).thenReturn(false);

        // when
        InvalidCurrentPasswordException exception =
                assertThrows(
                        InvalidCurrentPasswordException.class,
                        () -> memberService.changePassword(
                                requestEmail,
                                request
                        )
                );

        // then
        assertEquals(
                "현재 비밀번호가 올바르지 않습니다.",
                exception.getMessage()
        );

        verify(memberRepository)
                .findByEmailForUpdate(normalizedEmail);

        verify(passwordEncoder).matches(
                "wrong-current-password",
                "stored-password-hash"
        );

        /*
         * 현재 비밀번호가 틀렸으므로
         * 새 비밀번호 암호화와 저장은 실행되면 안 됩니다.
         */
        verify(passwordEncoder, never())
                .encode(anyString());

        verify(member, never())
                .changePasswordHash(anyString());

        verify(memberRepository, never())
                .saveAndFlush(any(Member.class));

        verify(persistentRefreshTokenService, never())
                .revokeAllActiveTokens(any(Member.class));
    }

    /**
     * 정상 요청이면 새 비밀번호를 BCrypt로 암호화하고,
     * 암호화된 값으로 Member의 비밀번호 해시를 변경해야 합니다.
     */
    @Test
    void changePassword_shouldEncodeAndChangePasswordHash() {
        // given
        String email = "user@example.com";

        Member member = mock(Member.class);

        ChangePasswordRequest request = createRequest(
                "current-password",
                "new-password-123",
                "new-password-123"
        );

        /*
         * 비밀번호 변경용 이메일 인증을
         * 이미 완료한 사용자로 설정합니다.
         */
        when(emailVerificationService
                .isPasswordChangeVerified(email))
                .thenReturn(true);

        when(memberRepository
                .findByEmailForUpdate(email))
                .thenReturn(Optional.of(member));

        when(member.getPasswordHash())
                .thenReturn("stored-password-hash");

        /*
         * 현재 비밀번호는 올바릅니다.
         */
        when(passwordEncoder.matches(
                "current-password",
                "stored-password-hash"
        )).thenReturn(true);

        /*
         * 새 비밀번호는 현재 비밀번호와 다릅니다.
         *
         * 이 설정이 false여야 SamePasswordException이 발생하지 않고
         * 실제 비밀번호 변경 단계로 진행됩니다.
         */
        when(passwordEncoder.matches(
                "new-password-123",
                "stored-password-hash"
        )).thenReturn(false);

        when(passwordEncoder
                .encode("new-password-123"))
                .thenReturn(
                        "encoded-new-password-hash"
                );

        // when
        ChangePasswordResponse response =
                memberService.changePassword(
                        email,
                        request
                );

        // then
        assertEquals(
                "SUCCESS",
                response.code()
        );

        assertEquals(
                "비밀번호가 변경되었습니다. 다시 로그인해 주세요.",
                response.message()
        );

        verify(passwordEncoder)
                .encode("new-password-123");

        verify(member)
                .changePasswordHash(
                        "encoded-new-password-hash"
                );

        verify(memberRepository)
                .saveAndFlush(member);

        verify(emailVerificationService)
                .removePasswordChangeVerification(
                        email
                );
    }

    /**
     * 정상적으로 비밀번호를 변경한 뒤에는
     * 모든 활성 Refresh Token을 폐기해야 합니다.
     *
     * 호출 순서:
     * 비밀번호 해시 변경
     * → DB 반영
     * → Refresh Token 전체 폐기
     */
    @Test
    void changePassword_shouldRevokeTokensAfterSavingPassword() {
        // given
        String email = "user@example.com";

        Member member = mock(Member.class);

        ChangePasswordRequest request = createRequest(
                "current-password",
                "new-password-123",
                "new-password-123"
        );

        when(emailVerificationService
                .isPasswordChangeVerified(email))
                .thenReturn(true);

        when(memberRepository
                .findByEmailForUpdate(email))
                .thenReturn(Optional.of(member));

        when(member.getPasswordHash())
                .thenReturn("stored-password-hash");

        when(passwordEncoder.matches(
                "current-password",
                "stored-password-hash"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "new-password-123",
                "stored-password-hash"
        )).thenReturn(false);

        when(passwordEncoder
                .encode("new-password-123"))
                .thenReturn(
                        "encoded-new-password-hash"
                );

        // when
        memberService.changePassword(
                email,
                request
        );

        // then
        InOrder callOrder = inOrder(
                member,
                memberRepository,
                persistentRefreshTokenService,
                emailVerificationService
        );

        callOrder.verify(member)
                .changePasswordHash(
                        "encoded-new-password-hash"
                );

        callOrder.verify(memberRepository)
                .saveAndFlush(member);

        callOrder.verify(persistentRefreshTokenService)
                .revokeAllActiveTokens(member);

        callOrder.verify(emailVerificationService)
                .removePasswordChangeVerification(
                        email
                );
    }


    /**
     * 비밀번호 변경용 이메일 인증을 완료하지 않았다면
     * 비밀번호 변경을 허용하면 안 됩니다.
     */
    @Test
    void changePassword_shouldRejectUnverifiedEmail() {
        // given
        String email = "user@example.com";

        ChangePasswordRequest request = createRequest(
                "current-password",
                "new-password-123",
                "new-password-123"
        );

        when(emailVerificationService
                .isPasswordChangeVerified(email))
                .thenReturn(false);

        // when
        EmailVerificationException exception =
                assertThrows(
                        EmailVerificationException.class,
                        () -> memberService.changePassword(
                                email,
                                request
                        )
                );

        // then
        assertEquals(
                "비밀번호 변경을 위한 이메일 인증을 완료해 주세요.",
                exception.getMessage()
        );

        /*
         * 이메일 인증을 통과하지 못했으므로
         * 회원 조회나 비밀번호 검증을 실행하면 안 됩니다.
         */
        verifyNoInteractions(
                memberRepository,
                passwordEncoder,
                persistentRefreshTokenService
        );
    }

    /**
     * 현재 사용 중인 비밀번호와 같은 값을
     * 새 비밀번호로 다시 사용할 수 없습니다.
     */
    @Test
    void changePassword_shouldRejectSamePassword() {
        // given
        String email = "user@example.com";

        Member member = mock(Member.class);

        ChangePasswordRequest request = createRequest(
                "current-password",
                "current-password",
                "current-password"
        );

        when(emailVerificationService
                .isPasswordChangeVerified(email))
                .thenReturn(true);

        when(memberRepository
                .findByEmailForUpdate(email))
                .thenReturn(Optional.of(member));

        when(member.getPasswordHash())
                .thenReturn("stored-password-hash");

        /*
         * 현재 비밀번호 확인과 새 비밀번호 중복 확인에
         * 같은 문자열이 전달됩니다.
         *
         * 두 호출 모두 true를 반환하므로:
         * - 현재 비밀번호 검증은 통과
         * - 새 비밀번호가 현재 비밀번호와 같다는 검사에서 차단
         */
        when(passwordEncoder.matches(
                "current-password",
                "stored-password-hash"
        )).thenReturn(true);

        // when
        SamePasswordException exception =
                assertThrows(
                        SamePasswordException.class,
                        () -> memberService.changePassword(
                                email,
                                request
                        )
                );

        // then
        assertEquals(
                "새 비밀번호는 현재 비밀번호와 다르게 입력해 주세요.",
                exception.getMessage()
        );

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(member, never())
                .changePasswordHash(anyString());

        verify(memberRepository, never())
                .saveAndFlush(any(Member.class));

        verify(persistentRefreshTokenService, never())
                .revokeAllActiveTokens(any(Member.class));
    }


    /**
     * 테스트마다 요청 객체 생성 코드가 반복되는 것을 줄이기 위한
     * 테스트 전용 도우미 메서드입니다.
     */
    private ChangePasswordRequest createRequest(
            String currentPassword,
            String newPassword,
            String newPasswordConfirm
    ) {
        ChangePasswordRequest request =
                new ChangePasswordRequest();

        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setNewPasswordConfirm(newPasswordConfirm);

        return request;
    }
}