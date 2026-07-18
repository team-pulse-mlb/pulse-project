package com.pulse.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PULSE API",
                version = "v1",
                description = """
                        스포일러 프리 MLB 관전 타이밍 추천 서비스의 REST API 명세입니다.
                        보호 모드와 공개 모드의 응답 차이는 각 경기 API 설명과 응답 스키마를 따릅니다.
                        SSE 연결 정책과 내부 메시지·캐시 계약은 docs/design/API_CONTRACTS.md를 참조합니다.
                        """,
                license = @License(name = "Private")
        )
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "로그인 API에서 발급받은 액세스 토큰"
)
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
}
