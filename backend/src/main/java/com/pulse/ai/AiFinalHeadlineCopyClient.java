package com.pulse.ai;

import com.pulse.common.ai.AiCopyContextReader;
import com.pulse.common.ai.AiCopyMode;
import com.pulse.common.ai.AiCopyResult;
import com.pulse.common.ai.FinalHeadlineCopyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * {@link FinalHeadlineCopyClient}의 ai-service 기반 구현체입니다.
 *
 * <p>전체 흐름:</p>
 * <ol>
 *     <li>{@link AiCopyContextReader}에서 FINAL_HEADLINE safeContext를 조회한다.</li>
 *     <li>{@link AiFinalHeadlineContextMapper}로 ai-service 요청 DTO를 만든다.</li>
 *     <li>{@link AiServiceClient}로 POST /ai/final-headline을 호출한다.</li>
 *     <li>ai-service 응답을 공통 {@link AiCopyResult}로 변환한다.</li>
 * </ol>
 *
 * <p>주의:</p>
 * <ul>
 *     <li>safeContext가 없으면 생성 대상이 아니므로 Optional.empty()를 반환합니다.</li>
 *     <li>ai-service 호출 실패도 Optional.empty()로 반환합니다.</li>
 *     <li>이 구현체는 DB 저장이나 fallback 문구 생성을 하지 않습니다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFinalHeadlineCopyClient implements FinalHeadlineCopyClient {

    private final AiCopyContextReader aiCopyContextReader;
    private final AiFinalHeadlineContextMapper aiFinalHeadlineContextMapper;
    private final AiServiceClient aiServiceClient;

    /**
     * 종료 경기 헤드라인 AI 문구 생성을 요청합니다.
     *
     * @param gameId 경기 ID
     * @param mode PROTECTED 또는 REVEALED
     * @return AI 문구 생성·검수 결과. 생성 대상이 아니거나 호출 실패 시 Optional.empty()
     */
    @Override
    public Optional<AiCopyResult> generateFinalHeadline(
            long gameId,
            AiCopyMode mode
    ) {
        return aiCopyContextReader.finalHeadlineContext(gameId, mode)
                // FinalHeadlineContext는 이미 검증된 safeContext이므로,
                // mapper에서는 HTTP 요청 DTO 형태로만 변환합니다.
                .map(aiFinalHeadlineContextMapper::toRequest)
                // ai-service 호출 성공 후에도 저장 불가 응답일 수 있으므로,
                // spoilerSafe/fallbackUsed/safeTitle/violations를 진단 로그로 남깁니다.
                .flatMap(request -> aiServiceClient.generateFinalHeadline(request)
                        .map(response -> {
                            logAiFinalHeadlineResponse(request, response);
                            return response;
                        }))
                // ai-service 응답 DTO를 Spring Boot 내부 공통 결과 모델로 변환합니다.
                .map(this::toAiCopyResult);
    }

    /**
     * ai-service HTTP 응답 중 저장 조건을 만족하지 못하는 케이스만 로그로 남깁니다.
     *
     * <p>HTTP 200이어도 {@code spoilerSafe=false}, {@code fallbackUsed=true},
     * {@code safeTitle} 누락/blank면 Spring Boot 저장 조건을 통과하지 못해
     * NOT_GENERATED가 될 수 있습니다.</p>
     *
     * <p>{@code safeTitle} 본문은 스포일러 가능성이 있으므로 로그에 남기지 않고,
     * 원인 분석에 필요한 상태값과 violations만 기록합니다.</p>
     */
    private void logAiFinalHeadlineResponse(
            AiFinalHeadlineRequest request,
            AiCopyResponse response
    ) {
        if (response.spoilerSafe()
                && !response.fallbackUsed()
                && response.safeTitle() != null
                && !response.safeTitle().isBlank()) {
            return;
        }

        log.warn(
                "ai-service final headline not storable. gameId={} mode={} spoilerSafe={} fallbackUsed={} safeTitleBlank={} contextHash={} violations={}",
                request.gameId(),
                request.mode(),
                response.spoilerSafe(),
                response.fallbackUsed(),
                response.safeTitle() == null || response.safeTitle().isBlank(),
                response.contextHash(),
                response.violations()
        );
    }

    /**
     * ai-service HTTP 응답을 Spring Boot 내부 공통 결과 모델로 변환합니다.
     *
     * <p>저장 여부 판단은 이 단계에서 하지 않습니다.
     * 호출부가 spoilerSafe, fallbackUsed, safeTitle, contextHash를 기준으로 결정합니다.</p>
     */
    private AiCopyResult toAiCopyResult(
            AiCopyResponse response
    ) {
        return new AiCopyResult(
                response.spoilerSafe(),
                response.contextHash(),
                response.safeTitle(),
                copyViolations(response.violations()),
                response.fallbackUsed()
        );
    }

    /**
     * violations를 null-safe 불변 리스트로 변환합니다.
     */
    private List<String> copyViolations(
            List<String> violations
    ) {
        if (violations == null || violations.isEmpty()) {
            return List.of();
        }

        return List.copyOf(violations);
    }
}
