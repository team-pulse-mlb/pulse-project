package com.pulse.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Spring Boot에서 ai-service로 HTTP 요청을 보내는 전용 client입니다.
 *
 * <p>이 client는 ai-service 호출만 담당합니다.</p>
 *
 * <p>책임 범위:</p>
 * <ul>
 *     <li>POST /ai/event-copy 호출</li>
 *     <li>POST /ai/final-headline 호출</li>
 *     <li>RestClient 예외를 Optional.empty()로 변환</li>
 * </ul>
 *
 * <p>책임이 아닌 것:</p>
 * <ul>
 *     <li>AI 문구 저장 여부 판단</li>
 *     <li>DB 저장</li>
 *     <li>contextHash 최신성 재검증</li>
 *     <li>fallback 문구 생성</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServiceClient {

    private static final String EVENT_COPY_PATH = "/ai/event-copy";
    private static final String FINAL_HEADLINE_PATH = "/ai/final-headline";
    private static final String PLAY_TRANSLATION_PATH = "/ai/play-translation";

    private final RestClient aiServiceRestClient;

    /**
     * EVENT_COPY 문구 생성을 ai-service에 요청합니다.
     *
     * <p>ai-service는 생성·검수 결과만 반환하고,
     * 저장 여부 판단과 DB 저장은 Spring Boot 후속 흐름이 담당합니다.</p>
     *
     * @param request /ai/event-copy 요청 DTO
     * @return ai-service 응답. 호출 실패 또는 응답 파싱 실패 시 Optional.empty()
     */
    public Optional<AiCopyResponse> generateEventCopy(AiEventCopyRequest request) {
        try {
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

    /**
     * 공개 모드 최근 플레이 번역을 ai-service에 요청합니다.
     *
     * 호출 실패나 응답 파싱 실패는 scorer 흐름을 중단하지 않고
     * 번역 미생성 상태로 처리한다.
     *
     * @param request /ai/play-translation 요청 DTO
     * @return ai-service 응답. 호출 실패 시 Optional.empty()
     */
    public Optional<AiPlayTranslationResponse> generatePlayTranslation(
            AiPlayTranslationRequest request
    ) {
        try {
            AiPlayTranslationResponse response = aiServiceRestClient.post()
                    .uri(PLAY_TRANSLATION_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiPlayTranslationResponse.class);

            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            /*
             * 원문은 최근 플레이 API의 폴백으로 계속 제공되므로
             * 번역 장애가 전체 경기 처리 흐름을 중단하지 않게 한다.
             */
            log.warn(
                    "ai-service play translation request failed. gameId={} playId={}",
                    request.gameId(),
                    request.playId(),
                    exception
            );

            return Optional.empty();
        }
    }


    /**
     * FINAL_HEADLINE 문구 생성을 ai-service에 요청합니다.
     *
     * <p>contextHash는 request에 담긴 값을 그대로 ai-service에 전달합니다.
     * ai-service는 이 값을 응답에 그대로 반환하고, Spring Boot가 저장 시점에 최신성을 판단합니다.</p>
     *
     * @param request /ai/final-headline 요청 DTO
     * @return ai-service 응답. 호출 실패 또는 응답 파싱 실패 시 Optional.empty()
     */
    public Optional<AiCopyResponse> generateFinalHeadline(AiFinalHeadlineRequest request) {
        try {
            AiCopyResponse response = aiServiceRestClient.post()
                    .uri(FINAL_HEADLINE_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiCopyResponse.class);

            return Optional.ofNullable(response);
        } catch (RestClientException exception) {
            // FINAL_HEADLINE 생성 실패도 EVENT_COPY와 동일하게
            // 전체 scorer 흐름을 중단하지 않고 Optional.empty()로 내려보냅니다.
            log.warn(
                    "ai-service final headline request failed. gameId={} mode={}",
                    request.gameId(),
                    request.mode(),
                    exception
            );

            return Optional.empty();
        }
    }
}
