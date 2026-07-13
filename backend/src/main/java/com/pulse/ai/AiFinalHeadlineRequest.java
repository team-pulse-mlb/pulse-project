package com.pulse.ai;

import java.util.List;

/**
 * ai-service의 POST /ai/final-headline 요청 DTO입니다.
 *
 * <p>Spring Boot 내부의 {@code FinalHeadlineContext}를 ai-service HTTP 계약에 맞게 변환한 형태입니다.</p>
 *
 * <p>주의:</p>
 * <ul>
 *     <li>contextHash는 Spring Boot가 계산한 값을 그대로 전달합니다.</li>
 *     <li>ai-service는 이 값을 재계산하지 않고 응답에 그대로 돌려줍니다.</li>
 *     <li>PROTECTED 모드에서는 finalScore, winner가 null일 수 있습니다.</li>
 *     <li>REVEALED 모드에서만 finalScore, winner가 포함될 수 있습니다.</li>
 * </ul>
 */
public record AiFinalHeadlineRequest(
        long gameId,
        String mode,
        String contextHash,
        SafeContext safeContext
) {

    /**
     * /ai/final-headline safeContext payload입니다.
     *
     * <p>이 객체에는 ai-service가 프롬프트 생성에 사용할 수 있는 안전 필드만 포함합니다.
     * raw play, 팀 상세, 시작 시간, recentPlays 같은 원본 경기 데이터는 포함하지 않습니다.</p>
     */
    public record SafeContext(
            String gameStatus,
            String inningPhase,
            List<String> safeTags,
            List<String> reasonCodes,
            List<KeyMoment> keyMoments,
            FinalScore finalScore,
            String winner
    ) {

        /**
         * 리스트 필드는 null 대신 빈 리스트로 정규화합니다.
         * 이렇게 하면 JSON 직렬화와 테스트에서 null 분기 처리를 줄일 수 있습니다.
         */
        public SafeContext {
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
     *
     * <p>PROTECTED 모드에서는 null이어야 합니다.</p>
     */
    public record FinalScore(
            Integer home,
            Integer away
    ) {
    }
}
