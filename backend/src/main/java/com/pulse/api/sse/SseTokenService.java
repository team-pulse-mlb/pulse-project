package com.pulse.api.sse;

import com.pulse.api.user.domain.Member;
import com.pulse.api.user.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * 인증된 SSE 연결에 사용할 1회용 단기 토큰을 관리합니다.
 *
 * 브라우저의 EventSource는 일반적인 방식으로
 * Authorization 헤더를 추가하기 어렵습니다.
 *
 * 따라서 다음 순서로 인증 연결을 만듭니다.
 *
 * 1. JWT 인증이 가능한 POST /api/sse/token 호출
 * 2. 서버가 Redis에 1회용 토큰 저장
 * 3. 브라우저가 GET /api/sse?token=...으로 연결
 * 4. 서버가 토큰을 조회하면서 즉시 삭제
 * 5. 토큰에 연결된 userId로 인증 SSE 연결 등록
 *
 * Access Token 자체를 URL에 넣지 않으므로
 * 브라우저 기록이나 프록시 로그에 JWT가 노출되는 것을 방지합니다.
 */
@Service
@RequiredArgsConstructor
public class SseTokenService {

    /**
     * Redis에 저장할 SSE 토큰 키의 접두사입니다.
     *
     * 예:
     * sse:token:abc123...
     */
    private static final String KEY_PREFIX =
            "sse:token:";

    /**
     * API 계약에 따른 SSE 1회용 토큰 유효기간입니다.
     */
    static final Duration TOKEN_TTL =
            Duration.ofSeconds(60);

    /**
     * 랜덤 토큰 원본 바이트 수입니다.
     *
     * 32바이트는 256비트의 난수이므로
     * 토큰을 추측하기 매우 어렵습니다.
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 극히 낮은 확률로 같은 토큰이 생성됐을 때
     * 다시 생성할 최대 횟수입니다.
     */
    private static final int MAX_GENERATION_ATTEMPTS = 5;

    private final StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;

    /**
     * 일반 Random이 아니라 보안용 난수를 생성하는 객체입니다.
     */
    private final SecureRandom secureRandom =
            new SecureRandom();

    /**
     * 현재 로그인 사용자의 SSE 1회용 토큰을 발급합니다.
     *
     * Redis 저장 구조:
     *
     * key:
     * sse:token:{임의 토큰}
     *
     * value:
     * 사용자 PK
     *
     * TTL:
     * 60초
     *
     * @param email Spring Security principal의 사용자 이메일
     * @return 브라우저가 GET /api/sse?token=에 사용할 토큰
     */
    public String issue(String email) {
        Member member =
                findMemberByEmail(email);

        /*
         * setIfAbsent()를 사용하므로 우연히 기존 토큰과
         * 같은 값이 생성돼도 기존 사용자 정보를 덮어쓰지 않습니다.
         */
        for (
                int attempt = 0;
                attempt < MAX_GENERATION_ATTEMPTS;
                attempt++
        ) {
            String token =
                    generateToken();

            Boolean stored =
                    redisTemplate
                            .opsForValue()
                            .setIfAbsent(
                                    createKey(token),
                                    String.valueOf(
                                            member.getUserId()
                                    ),
                                    TOKEN_TTL
                            );

            if (Boolean.TRUE.equals(stored)) {
                return token;
            }
        }

        throw new IllegalStateException(
                "SSE 연결 토큰을 생성하지 못했습니다."
        );
    }

    /**
     * SSE 연결 요청에 전달된 토큰을 한 번만 소모합니다.
     *
     * getAndDelete()를 사용하는 이유:
     *
     * 1. Redis에서 userId를 조회하고
     * 2. 동일한 명령 과정에서 토큰을 삭제합니다.
     *
     * 따라서 같은 토큰으로 두 번째 연결을 시도하면
     * 더 이상 userId를 얻을 수 없습니다.
     *
     * @param token GET /api/sse?token=에 전달된 토큰
     * @return 유효한 토큰이면 사용자 PK, 아니면 빈 값
     */
    public OptionalLong consumeUserId(String token) {
        if (token == null || token.isBlank()) {
            return OptionalLong.empty();
        }

        String userIdValue =
                redisTemplate
                        .opsForValue()
                        .getAndDelete(
                                createKey(token.trim())
                        );

        /*
         * 토큰이 없다는 것은 다음 중 하나입니다.
         *
         * - 발급되지 않은 토큰
         * - 60초가 지나 만료된 토큰
         * - 이미 한 번 사용된 토큰
         */
        if (userIdValue == null) {
            return OptionalLong.empty();
        }

        try {
            return OptionalLong.of(
                    Long.parseLong(userIdValue)
            );
        } catch (NumberFormatException exception) {
            /*
             * Redis 값이 예상과 달리 userId 숫자가 아니라면
             * 인증 연결로 취급하지 않습니다.
             *
             * getAndDelete()로 이미 삭제됐기 때문에
             * 잘못된 값이 Redis에 계속 남지도 않습니다.
             */
            return OptionalLong.empty();
        }
    }

    /**
     * 이메일을 정규화한 뒤 Member를 조회합니다.
     */
    private Member findMemberByEmail(String email) {
        String normalizedEmail =
                email.trim()
                        .toLowerCase(Locale.ROOT);

        return memberRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "가입되지 않은 이메일입니다."
                        )
                );
    }

    /**
     * URL 쿼리 파라미터로 안전하게 사용할 수 있는
     * 랜덤 토큰을 생성합니다.
     *
     * Base64 URL Encoder를 사용하므로
     * '+', '/', '=' 같은 URL 처리에 불편한 문자를 제외합니다.
     */
    private String generateToken() {
        byte[] randomBytes =
                new byte[TOKEN_BYTES];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    /**
     * Redis SSE 토큰 키를 생성합니다.
     */
    private String createKey(String token) {
        return KEY_PREFIX + token;
    }
}