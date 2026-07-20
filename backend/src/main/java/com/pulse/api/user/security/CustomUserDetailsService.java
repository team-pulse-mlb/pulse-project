package com.pulse.api.user.security;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.domain.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = email
                .trim()
                .toLowerCase(Locale.ROOT);

        Member member = memberRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "가입되지 않은 이메일입니다."
                        )
                );

        if (member.getStatus()
                != MemberStatus.ACTIVE) {

            throw new UsernameNotFoundException(
                    "로그인할 수 없는 계정입니다."
            );
        }

        return User.builder()
                .username(member.getEmail())
                .password(member.getPasswordHash())
                .authorities("ROLE_" + member.getRole().name())
                .build();
    }
}