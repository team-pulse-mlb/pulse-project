package com.pulse.scorer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 전체 AI 문구 재처리 배치 설정입니다.
 *
 * windowStartDate·windowEndDate(KST, yyyy-MM-dd, 종료일 포함)를 둘 다 지정하면
 * 해당 기간에 시작한 종료 경기만 대상으로 재처리한다. 비우면 기존과 동일하게
 * 전체 종료 경기를 대상으로 한다.
 */
@ConfigurationProperties(prefix = "pulse.ai-copy-reprocess")
public record AiCopyReprocessProperties(int eventBatchSize, String windowStartDate, String windowEndDate) {

    public AiCopyReprocessProperties {
        if (eventBatchSize <= 0) {
            eventBatchSize = 100;
        }
        if (windowStartDate != null && windowStartDate.isBlank()) {
            windowStartDate = null;
        }
        if (windowEndDate != null && windowEndDate.isBlank()) {
            windowEndDate = null;
        }
        if ((windowStartDate == null) != (windowEndDate == null)) {
            throw new IllegalStateException(
                    "pulse.ai-copy-reprocess.window-start-date와 window-end-date는 함께 지정해야 한다");
        }
    }

    public boolean hasWindow() {
        return windowStartDate != null;
    }
}
