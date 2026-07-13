package com.pulse.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private static final String EVENT_COPY_PATH = "/ai/event-copy";

    private final RestClient aiServiceRestClient;

    public Optional<AiCopyResponse> generateEventCopy(AiEventCopyRequest request) {
        try {
            // ai-service는 생성·검수 결과만 반환하고,
            // 저장 여부 판단과 DB 저장은 Spring Boot가 담당합니다.
            AiCopyResponse response = aiServiceRestClient.post()
                    .uri(EVENT_COPY_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiCopyResponse.class);

            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            // ai-service 장애, timeout, 4xx/5xx, 응답 파싱 실패는
            // scorer 흐름을 깨지 않고 "문구 미생성"으로 처리합니다.
            log.warn(
                    "ai-service event copy request failed. gameId={} eventId={} mode={}",
                    request.gameId(),
                    request.eventId(),
                    request.mode(),
                    exception
            );

            return Optional.empty();
        }
    }
}