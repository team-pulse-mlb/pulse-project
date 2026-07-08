package com.pulse.api.user.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    // JWT에 서명하고 검증할 때 사용하는 서버 비밀키
    private final SecretKey signingKey;
    // 받은 토큰을 열어보고 진짜인지 검사하는 도구
    private final JwtParser jwtParser;

    public JwtTokenProvider(
            JwtProperties jwtProperties
    ) {
        this.jwtProperties = jwtProperties;

        // 환경변수에 저장한 Base64 문자열을 원래 byte[]로 변환
        byte[] keyBytes = Decoders.BASE64URL.decode(
                jwtProperties.secret()
        );

        // byte[]를 JWT 서명에 사용할 SecretKey 객체로 변환
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);

        // 전달받은 JWT를 검증하고 읽을 Parser 생성
        // 앞으로 들어오는 JWT는 이 서버의 signingKey로 서명된 토큰인지 확인
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .build();
    }

    /**
     * API 접근에 사용할 Access Token 생성
     */
    public String createAccessToken(
            String email,
            String role
    ) {
        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plus(
                jwtProperties.accessTokenExpiration()
        );

        return Jwts.builder()
                // 이 토큰의 주인
                .subject(email)

                // 사용자의 권한
                .claim("role", role)

                // Access Token과 Refresh Token 구분용
                .claim("tokenType", "access")

                // 토큰 발급 시간
                .issuedAt(Date.from(issuedAt))

                // 토큰 만료 시간
                .expiration(Date.from(expiresAt))

                // 서버 비밀키로 서명
                .signWith(signingKey)

                // Header.Payload.Signature 문자열 생성
                .compact();
    }


    /**
     * Access Token 재발급에 사용할 Refresh Token 생성
     */
    public String createRefreshToken(String email) {

        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plus(
                jwtProperties.refreshTokenExpiration()
        );

        return Jwts.builder()
                // JWT ID
                // Refresh Token마다 고유값을 넣어 같은 사용자의 토큰도 서로 다르게 만든다.
                .id(UUID.randomUUID().toString())

                // 이 Refresh Token의 주인
                .subject(email)

                // 일반 API 접근용 토큰과 구분
                .claim("tokenType", "refresh")

                // 발급 시간
                .issuedAt(Date.from(issuedAt))

                // 만료 시간
                .expiration(Date.from(expiresAt))

                // 서버 비밀키로 서명
                .signWith(signingKey)

                // 최종 JWT 문자열 생성
                .compact();
    }


    /**
     * JWT의 서명과 만료시간을 검증하고 Payload를 반환
     */
    public Claims parseClaims(String token) {

        return jwtParser
                .parseSignedClaims(token)
                .getPayload();
    }



}

/*
    JwtProperties
    → 비밀키와 유효시간을 가져옴

    JwtTokenProvider 생성자
    → Base64 비밀키를 SecretKey로 변환

    createAccessToken()
    → 이메일·권한·시간을 Payload에 넣음
    → SecretKey로 Signature 생성
    → JWT 문자열 반환

    createRefreshToken()
    → 재발급에 사용할 Refresh Token 생성
*/