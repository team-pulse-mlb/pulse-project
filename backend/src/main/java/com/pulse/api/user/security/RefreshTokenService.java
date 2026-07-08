package com.pulse.api.user.security;

import com.pulse.api.user.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    /**
     * Refresh Token을 Redis에 저장
     */
    public void save(
            String email,
            String refreshToken
    ) {
        stringRedisTemplate.opsForValue().set(
                createKey(email),
                refreshToken,
                jwtProperties.refreshTokenExpiration()
        );
    }

    /**
     * Redis에 저장된 Refresh Token 조회
     */
    public String find(String email) {
        return stringRedisTemplate.opsForValue().get(
                createKey(email)
        );
    }

    /**
     * 로그아웃 시 Refresh Token 삭제
     */
    public void delete(String email) {
        stringRedisTemplate.delete(
                createKey(email)
        );
    }

    private String createKey(String email) {
        return KEY_PREFIX + normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }



}