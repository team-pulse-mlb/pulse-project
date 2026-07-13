package com.pulse.scorer;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 생성 트리거 기본 구현. 창현(com.pulse.ai)이 전체 구현 빈을 제공하면 그쪽이 우선한다.
 * 현재 연결 가능한 EVENT_COPY는 AiEventCopyGenerator에 위임한다.
 */
@Configuration
class NoOpAiGenerationTriggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiGenerationTrigger.class)
    AiGenerationTrigger noOpAiGenerationTrigger(AiEventCopyGenerator eventCopyGenerator) {
        return new NoOp(eventCopyGenerator);
    }

    @Slf4j
    static final class NoOp implements AiGenerationTrigger {

        private final AiEventCopyGenerator eventCopyGenerator;

        NoOp(AiEventCopyGenerator eventCopyGenerator) {
            this.eventCopyGenerator = eventCopyGenerator;
        }

        @Override
        public void onGameFinalized(long gameId, Instant occurredAt) {
            log.debug("AI 생성 트리거 미구현(no-op): final gameId={}", gameId);
        }

        @Override
        public void onGameEventPersisted(long gameId, long eventId, String mode, Instant occurredAt) {
            eventCopyGenerator.generate(gameId, eventId, mode);
        }
    }
}
