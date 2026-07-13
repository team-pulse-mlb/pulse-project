package com.pulse.api.user.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Refresh Token 원문을 SHA-256 해시로 변환하는 서비스
 *
 * DB에는 Refresh Token 원문을 저장하지 않고,
 * 이 서비스로 만든 해시값만 저장한다.
 */
@Service
public class RefreshTokenHashService {

    public String hash(String refreshToken) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashedBytes = messageDigest.digest(
                    refreshToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashedBytes);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 알고리즘을 사용할 수 없습니다.",
                    exception
            );
        }
    }
}