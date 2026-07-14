package com.pulse.common.ai;

import java.util.List;

/**
 * ai-service가 반환한 AI 문구 생성·검수 결과를 Spring Boot 내부에서 사용하는 공통 결과 모델입니다.
 *
 * <p>이 record는 FINAL_HEADLINE, EVENT_COPY 등 AI 문구 유형과 무관하게
 * 공통 저장 판단에 필요한 값만 담습니다.</p>
 *
 * <p>주의:</p>
 * <ul>
 *     <li>ai-service는 fallback 문구를 생성하지 않습니다.</li>
 *     <li>safeTitle은 spoiler guard를 통과한 경우에만 저장 후보로 사용할 수 있습니다.</li>
 *     <li>contextHash는 요청에 사용된 safeContext의 최신성 검증 기준입니다.</li>
 * </ul>
 */
public record AiCopyResult(
        boolean spoilerSafe,
        String contextHash,
        String safeTitle,
        List<String> violations,
        boolean fallbackUsed
) {

    /**
     * violations가 null로 들어와도 호출부에서 null 체크를 반복하지 않도록 빈 리스트로 정규화합니다.
     */
    public AiCopyResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
