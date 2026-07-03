package com.pulse.api.dto;

public record SignupResponse(   // record는 응답 데이터처럼 값만 보관하는 객체를 간단하게 만드는 Java 문법
        int result,
        String message
) {
}
