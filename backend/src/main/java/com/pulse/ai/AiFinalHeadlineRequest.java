package com.pulse.ai;

import java.util.List;

/**
 * ai-service의 POST /ai/final-headline 요청 DTO입니다.
 *
 * <p>Spring Boot 내부의 {@code FinalHeadlineContext}를 ai-service HTTP 계약에 맞게 변환한 형태입니다.</p>
 *
 * <p>중요:</p>
 * <ul>
 *     <li>contextHash는 Spring Boot가 계산한 값을 그대로 전달합니다.</li>
 *     <li>PROTECTED 요청 safeContext에는 점수와 승패 관련 key 자체가 없어야 합니다.</li>
 *     <li>REVEALED 요청 safeContext에서만 finalScore, winner를 포함할 수 있습니다.</li>
 * </ul>
 */
public record AiFinalHeadlineRequest(
        long gameId,
        String mode,
        String contextHash,
        SafeContext safeContext
) {

    /**
     * FINAL_HEADLINE safeContext의 공통 marker type입니다.
     *
     * <p>실제 JSON payload는 요청 모드에 따라
     * {@link ProtectedSafeContext} 또는 {@link RevealedSafeContext}로 직렬화됩니다.</p>
     */
    public sealed interface SafeContext
            permits ProtectedSafeContext, RevealedSafeContext {
    }

    /**
     * PROTECTED 모드 전용 safeContext입니다.
     *
     * <p>확정 계약상 PROTECTED 요청에는 finalScore, winner key 자체가 없어야 하므로
     * 이 record에는 해당 필드를 선언하지 않습니다.</p>
     */
    public record ProtectedSafeContext(
            String gameStatus,
            String inningPhase,
            List<String> safeTags,
            List<String> reasonCodes,
            List<KeyMoment> keyMoments
    ) implements SafeContext {

        /**
         * 리스트 필드는 null 대신 빈 리스트로 정규화합니다.
         */
        public ProtectedSafeContext {
            safeTags = safeTags == null ? List.of() : List.copyOf(safeTags);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            keyMoments = keyMoments == null ? List.of() : List.copyOf(keyMoments);
        }
    }

    /**
     * REVEALED 모드 전용 safeContext입니다.
     *
     * <p>공개 모드에서는 Spring Boot가 검증해 전달한 finalScore, winner만 포함합니다.
     * winner는 무승부 등 상황에서 null일 수 있으므로 class 전체 NON_NULL 전략으로 숨기지 않습니다.</p>
     */
    public record RevealedSafeContext(
            String gameStatus,
            String inningPhase,
            List<String> safeTags,
            List<String> reasonCodes,
            List<KeyMoment> keyMoments,
            FinalScore finalScore,
            String winner
    ) implements SafeContext {

        /**
         * 리스트 필드는 null 대신 빈 리스트로 정규화합니다.
         */
        public RevealedSafeContext {
            safeTags = safeTags == null ? List.of() : List.copyOf(safeTags);
            reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            keyMoments = keyMoments == null ? List.of() : List.copyOf(keyMoments);
        }
    }

    /**
     * 스포일러 없이 노출 가능한 핵심 순간입니다.
     *
     * <p>label은 이미 보호 표현으로 정제된 값이어야 합니다.</p>
     */
    public record KeyMoment(
            Integer inning,
            String label
    ) {
    }

    /**
     * REVEALED 모드에서만 사용할 수 있는 최종 점수 정보입니다.
     */
    public record FinalScore(
            Integer home,
            Integer away
    ) {
    }
}