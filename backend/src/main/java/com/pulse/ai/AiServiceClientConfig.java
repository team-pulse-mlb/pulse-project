package com.pulse.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiServiceClientConfig {

    @Bean
    public RestClient aiServiceRestClient(AiServiceProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(createRequestFactory(properties))
                .build();
    }

    private SimpleClientHttpRequestFactory createRequestFactory(
            AiServiceProperties properties
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return requestFactory;
    }
}