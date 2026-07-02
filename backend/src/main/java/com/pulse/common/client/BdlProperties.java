package com.pulse.common.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulse.bdl")
public record BdlProperties(String baseUrl, String apiKey) {
}
