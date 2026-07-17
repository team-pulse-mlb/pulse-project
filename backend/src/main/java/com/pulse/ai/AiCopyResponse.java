package com.pulse.ai;

import java.util.List;

/**
 * ai-service의 AI 문구 생성·검수 응답 DTO입니다.
 *
 * <p>FINAL_HEADLINE에서는 생성 근거인 usedFactIds와 usedPlayIds를
 * 함께 받을 수 있습니다.</p>
 *
 * <p>EVENT_COPY 응답에는 evidence 필드가 없을 수 있으므로
 * 누락된 리스트는 빈 불변 리스트로 정규화합니다.</p>
 */
public record AiCopyResponse(
        boolean spoilerSafe,
        String contextHash,
        String safeTitle,
        List<String> violations,
        boolean fallbackUsed,
        List<String> usedFactIds,
        List<Long> usedPlayIds
) {

    public AiCopyResponse {
        violations = immutableList(violations);
        usedFactIds = immutableList(usedFactIds);
        usedPlayIds = immutableList(usedPlayIds);
    }

    /**
     * 기존 EVENT_COPY 및 5개 인자 호출부와의 호환성을 유지합니다.
     */
    public AiCopyResponse(
            boolean spoilerSafe,
            String contextHash,
            String safeTitle,
            List<String> violations,
            boolean fallbackUsed
    ) {
        this(
                spoilerSafe,
                contextHash,
                safeTitle,
                violations,
                fallbackUsed,
                List.of(),
                List.of()
        );
    }

    private static <T> List<T> immutableList(
            List<T> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        return List.copyOf(values);
    }
}
