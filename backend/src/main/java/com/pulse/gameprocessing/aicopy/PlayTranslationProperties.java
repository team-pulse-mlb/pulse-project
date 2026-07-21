package com.pulse.gameprocessing.aicopy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 최근 플레이 AI 번역의 호출 제한과 배치 크기 설정이다. */
@ConfigurationProperties(prefix = "pulse.ai.play-translation")
public record PlayTranslationProperties(
        int maxAttempts,
        int batchSize
) {
}
