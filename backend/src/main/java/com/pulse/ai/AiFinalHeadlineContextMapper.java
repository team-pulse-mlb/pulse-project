package com.pulse.ai;

import com.pulse.common.ai.FinalHeadlineContext;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link FinalHeadlineContext}를 ai-service /ai/final-headline 요청 DTO로 변환합니다.
 *
 * <p>중요한 책임 경계:</p>
 * <ul>
 *     <li>{@code FinalHeadlineContext}는 {@code AiCopyContextReader}가 반환한 검증 완료 context입니다.</li>
 *     <li>이 mapper는 safeContext를 새로 판단하거나 contextHash를 재계산하지 않습니다.</li>
 *     <li>필드명을 ai-service HTTP 계약에 맞게 옮기는 역할만 합니다.</li>
 * </ul>
 */
@Component
public class AiFinalHeadlineContextMapper {

    /**
     * 검증 완료된 FINAL_HEADLINE context를 ai-service 요청 객체로 변환합니다.
     *
     * @param context Spring Boot가 생성한 FINAL_HEADLINE safeContext
     * @return ai-service POST /ai/final-headline 요청 DTO
     */
    public AiFinalHeadlineRequest toRequest(
            FinalHeadlineContext context
    ) {
        return new AiFinalHeadlineRequest(
                context.gameId(),
                context.mode().name(),
                context.contextHash(),
                toSafeContext(context)
        );
    }

    /**
     * FinalHeadlineContext의 safe field를 ai-service safeContext 계약에 맞게 옮깁니다.
     *
     * <p>매핑 기준:</p>
     * <ul>
     *     <li>status → gameStatus</li>
     *     <li>periodLabel → inningPhase</li>
     *     <li>reasonTags → safeTags</li>
     *     <li>spoilerSafeSignals → reasonCodes</li>
     *     <li>keyMoments → keyMoments</li>
     *     <li>finalScore → finalScore</li>
     *     <li>winner → winner</li>
     * </ul>
     */
    private AiFinalHeadlineRequest.SafeContext toSafeContext(
            FinalHeadlineContext context
    ) {
        return new AiFinalHeadlineRequest.SafeContext(
                context.status(),
                context.periodLabel(),
                // AI_COPY 계약 기준: reasonTags는 사용자에게 노출 가능한 보호형 태그입니다.
                copyList(context.reasonTags()),
                // AI_COPY 계약 기준: spoilerSafeSignals는 reasonCodes로 전달합니다.
                copyList(context.spoilerSafeSignals()),
                toKeyMoments(context.keyMoments()),
                toFinalScore(context.finalScore()),
                context.winner()
        );
    }

    /**
     * keyMoments는 이미 보호 표현으로 정제된 값이므로, 값의 의미를 바꾸지 않고 DTO 타입만 변환합니다.
     */
    private List<AiFinalHeadlineRequest.KeyMoment> toKeyMoments(
            List<FinalHeadlineContext.KeyMoment> keyMoments
    ) {
        if (keyMoments == null || keyMoments.isEmpty()) {
            return List.of();
        }

        return keyMoments.stream()
                .map(keyMoment -> new AiFinalHeadlineRequest.KeyMoment(
                        keyMoment.inning(),
                        keyMoment.label()
                ))
                .toList();
    }

    /**
     * REVEALED 모드에서만 finalScore가 들어올 수 있습니다.
     * PROTECTED 모드에서는 null 그대로 ai-service 요청에 전달합니다.
     */
    private AiFinalHeadlineRequest.FinalScore toFinalScore(
            FinalHeadlineContext.FinalScore finalScore
    ) {
        if (finalScore == null) {
            return null;
        }

        return new AiFinalHeadlineRequest.FinalScore(
                finalScore.home(),
                finalScore.away()
        );
    }

    /**
     * 리스트 필드를 불변 복사본으로 정규화합니다.
     */
    private List<String> copyList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return List.copyOf(values);
    }
}

