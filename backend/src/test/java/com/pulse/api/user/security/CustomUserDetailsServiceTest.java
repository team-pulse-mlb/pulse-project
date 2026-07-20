package com.pulse.api.user.security;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.MemberRole;
import com.pulse.api.user.domain.MemberStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomUserDetailsService가 회원 이메일과 상태를
 * 올바르게 검사하는지 확인하는 단위 테스트입니다.
 */
@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private CustomUserDetailsService
            customUserDetailsService;

    /**
     * ACTIVE 상태의 회원은 정상적으로 로그인 정보를
     * 반환해야 합니다.
     */
    @Test
    void loadUserByUsername_shouldReturnActiveMember() {
        // given
        String requestEmail =
                " USER@EXAMPLE.COM ";

        String normalizedEmail =
                "user@example.com";

        Member member = mock(Member.class);

        when(memberRepository
                .findByEmail(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(member.getStatus())
                .thenReturn(MemberStatus.ACTIVE);

        when(member.getEmail())
                .thenReturn(normalizedEmail);

        when(member.getPasswordHash())
                .thenReturn("stored-password-hash");

        when(member.getRole())
                .thenReturn(MemberRole.USER);

        // when
        UserDetails userDetails =
                customUserDetailsService
                        .loadUserByUsername(
                                requestEmail
                        );

        // then
        assertEquals(
                normalizedEmail,
                userDetails.getUsername()
        );

        assertEquals(
                "stored-password-hash",
                userDetails.getPassword()
        );

        assertEquals(
                "ROLE_USER",
                userDetails
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );

        /*
         * 입력 이메일의 공백이 제거되고
         * 소문자로 변환됐는지도 확인합니다.
         */
        verify(memberRepository)
                .findByEmail(normalizedEmail);
    }

    /**
     * WITHDRAWN 상태의 회원은 이메일과 비밀번호가
     * 올바르더라도 다시 로그인할 수 없어야 합니다.
     */
    @Test
    void loadUserByUsername_shouldRejectWithdrawnMember() {
        // given
        String requestEmail =
                " USER@EXAMPLE.COM ";

        String normalizedEmail =
                "user@example.com";

        Member member = mock(Member.class);

        when(memberRepository
                .findByEmail(normalizedEmail))
                .thenReturn(Optional.of(member));

        when(member.getStatus())
                .thenReturn(MemberStatus.WITHDRAWN);

        // when
        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService
                                .loadUserByUsername(
                                        requestEmail
                                )
                );

        // then
        assertEquals(
                "로그인할 수 없는 계정입니다.",
                exception.getMessage()
        );

        verify(memberRepository)
                .findByEmail(normalizedEmail);
    }

    /**
     * DB에 존재하지 않는 이메일은 로그인할 수 없어야 합니다.
     */
    @Test
    void loadUserByUsername_shouldRejectUnknownEmail() {
        // given
        String email =
                "unknown@example.com";

        when(memberRepository
                .findByEmail(email))
                .thenReturn(Optional.empty());

        // when
        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService
                                .loadUserByUsername(
                                        email
                                )
                );

        // then
        assertEquals(
                "가입되지 않은 이메일입니다.",
                exception.getMessage()
        );

        verify(memberRepository)
                .findByEmail(email);
    }
}