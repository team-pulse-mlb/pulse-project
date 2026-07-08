package com.pulse.scorer;

import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 생성 트리거 기본 구현. 창현(com.pulse.ai)이 실제 빈을 제공하면 그쪽이 우선한다.
 */
@Configuration
class NoOpAiGenerationTriggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiGenerationTrigger.class)
    AiGenerationTrigger noOpAiGenerationTrigger() {
        return new NoOp();
    }

    @Slf4j
    static final class NoOp implements AiGenerationTrigger {

        @Override
        public void onLiveSignalChange(long gameId, List<String> tags, Instant occurredAt) {
            log.debug("AI 생성 트리거 미구현(no-op): live gameId={}, tags={}", gameId, tags);
        }

        @Override
        public void onGameFinalized(long gameId, Instant occurredAt) {
            log.debug("AI 생성 트리거 미구현(no-op): final gameId={}", gameId);
        }
    }
}
