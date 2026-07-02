package com.pulse.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "PULSE API",
                version = "v1",
                description = "PULSE 백엔드 API 명세"
        )
)
public class OpenApiConfig {
}
