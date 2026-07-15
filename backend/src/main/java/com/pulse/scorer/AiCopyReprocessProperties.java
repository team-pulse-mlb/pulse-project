package com.pulse.scorer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 전체 AI 문구 재처리 배치 설정입니다. */
@ConfigurationProperties(prefix = "pulse.ai-copy-reprocess")
public record AiCopyReprocessProperties(int eventBatchSize) {

    public AiCopyReprocessProperties {
        if (eventBatchSize <= 0) {
            eventBatchSize = 100;
        }
    }
}
