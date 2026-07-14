package com.pulse.scorer;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 생성 트리거 기본 구현. 창현(com.pulse.ai)이 전체 구현 빈을 제공하면 그쪽이 우선한다.
 * 현재 연결 가능한 EVENT_COPY는 AiEventCopyGenerator, FINAL_HEADLINE은 AiFinalHeadlineGenerator에 위임한다.
 * 트리거를 호출하는 GameEventExtractor·GameFinalizationService가 scorer 역할에서만 등록되므로
 * 이 설정도 같은 조건을 따른다.
 */
@Configuration
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
class NoOpAiGenerationTriggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(AiGenerationTrigger.class)
    AiGenerationTrigger noOpAiGenerationTrigger(
            AiEventCopyGenerator eventCopyGenerator,
            AiFinalHeadlineGenerator finalHeadlineGenerator) {
        return new NoOp(eventCopyGenerator, finalHeadlineGenerator);
    }

    @Slf4j
    static final class NoOp implements AiGenerationTrigger {

        private final AiEventCopyGenerator eventCopyGenerator;
        private final AiFinalHeadlineGenerator finalHeadlineGenerator;

        NoOp(AiEventCopyGenerator eventCopyGenerator, AiFinalHeadlineGenerator finalHeadlineGenerator) {
            this.eventCopyGenerator = eventCopyGenerator;
            this.finalHeadlineGenerator = finalHeadlineGenerator;
        }

        @Override
        public void onGameFinalized(long gameId, Instant occurredAt) {
            finalHeadlineGenerator.generate(gameId);
        }

        @Override
        public void onGameEventPersisted(long gameId, long eventId, String mode, Instant occurredAt) {
            eventCopyGenerator.generate(gameId, eventId, mode);
        }
    }
}
