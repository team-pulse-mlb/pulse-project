package com.pulse.common.ai;

import java.util.List;

/**
 * ai-service가 반환한 AI 문구 생성·검수 결과를
 * Spring Boot 내부에서 사용하는 공통 결과 모델입니다.
 *
 * <p>FINAL_HEADLINE에서는 usedFactIds와 usedPlayIds가
 * safeTitle의 생성 근거로 전달됩니다.</p>
 *
 * <p>EVENT_COPY에서는 두 evidence 목록이 비어 있습니다.</p>
 */
public record AiCopyResult(
        boolean spoilerSafe,
        String contextHash,
        String safeTitle,
        List<String> violations,
        boolean fallbackUsed,
        List<String> usedFactIds,
        List<Long> usedPlayIds
) {

    public AiCopyResult {
        violations = immutableList(violations);
        usedFactIds = immutableList(usedFactIds);
        usedPlayIds = immutableList(usedPlayIds);
    }

    /**
     * 기존 EVENT_COPY 및 5개 인자 호출부와의 호환성을 유지합니다.
     */
    public AiCopyResult(
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
