package com.pulse.api.user;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import com.pulse.api.user.dto.LoginRequest;
import com.pulse.api.user.dto.LoginResponse;
import com.pulse.api.user.exception.LoginFailedException;
import com.pulse.api.user.security.PersistentRefreshTokenService;
import com.pulse.api.user.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;

    // refresh_tokens DB 테이블에 Refresh Token 해시를 저장하는 서비스
    private final PersistentRefreshTokenService persistentRefreshTokenService;

    // 로그인 성공 후 refresh_tokens.user_id에 연결할 Member 조회용
    private final MemberRepository memberRepository;

    /**
     * 이메일과 비밀번호 로그인
     *
     * 처리 흐름:
     * 1. 이메일/비밀번호를 Spring Security에 인증 요청
     * 2. 인증 성공 시 Access Token 발급
     * 3. Refresh Token 발급
     * 4. Refresh Token 원문은 DB에 저장하지 않고 해시만 refresh_tokens 테이블에 저장
     * 5. Controller로 Access Token 응답과 Refresh Token 쿠키용 원문을 전달
     */
    public LoginResult login(LoginRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        // 아직 인증되지 않은 이메일·비밀번호 정보를 생성
        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(
                        email,
                        request.password()
                );

        try {
            // Spring Security에 실제 인증 요청
            Authentication authenticationResult =
                    authenticationManager.authenticate(
                            authenticationRequest
                    );

            String authenticatedEmail = authenticationResult.getName()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            // refresh_tokens 테이블은 users.user_id와 연결되므로 Member 엔티티를 조회한다.
            Member member = memberRepository.findByEmail(authenticatedEmail)
                    .orElseThrow(() ->
                            new LoginFailedException(
                                    "회원 정보를 찾을 수 없습니다."
                            )
                    );

            // 인증된 사용자의 권한 가져오기
            String role = authenticationResult
                    .getAuthorities()
                    .stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse("ROLE_USER");

            // 인증된 사용자 정보로 Access Token 생성
            String accessToken =
                    jwtTokenProvider.createAccessToken(
                            authenticatedEmail,
                            role
                    );

            // Access Token 재발급용 Refresh Token 생성
            String refreshToken =
                    jwtTokenProvider.createRefreshToken(
                            authenticatedEmail
                    );

            // Refresh Token 원문은 저장하지 않고,
            // SHA-256 해시값만 DB refresh_tokens 테이블에 저장한다.
            //
            // 기존 Redis 방식처럼 email key 하나에 덮어쓰지 않으므로
            // 기기 2대 로그인도 서로 무효화하지 않는다.
            persistentRefreshTokenService.saveNewToken(
                    member,
                    refreshToken
            );

            // React에 보낼 JSON 응답
            LoginResponse response = new LoginResponse(
                    1,
                    "로그인에 성공했습니다.",
                    accessToken
            );

            // Controller에 JSON 응답과 Refresh Token 전달
            // Refresh Token 원문은 Controller에서 HttpOnly Cookie로 내려간다.
            return new LoginResult(
                    response,
                    refreshToken
            );

        } catch (AuthenticationException e) {
            throw new LoginFailedException(
                    "이메일 또는 비밀번호가 일치하지 않습니다."
            );
        }
    }
}