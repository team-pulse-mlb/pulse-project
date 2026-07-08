package com.pulse.api.user.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

// application.yml의 설정을 자바 객체로 가져오는 역할
@Validated
@ConfigurationProperties(prefix = "jwt")    // jwt: 아래에 있는 설정값을 이 객체에 담으라는 것
public record JwtProperties(

        @NotBlank
        String secret,

        @NotNull
        Duration accessTokenExpiration,

        @NotNull
        Duration refreshTokenExpiration
) {
}