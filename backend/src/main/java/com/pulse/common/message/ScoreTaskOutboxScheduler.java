package com.pulse.common.message;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** poller 또는 scorer 역할에서 미발행 ScoreTask를 주기적으로 재발행한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("(${pulse.poller.enabled:false} or ${pulse.scorer.enabled:true})"
        + " and ${pulse.score-task-outbox.scheduler-enabled:true}")
public class ScoreTaskOutboxScheduler {

    private final ScoreTaskOutboxDispatcher dispatcher;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    void registerMetrics() {
        Counter.builder("pulse.score.task.publish.failures").register(meterRegistry);
        Counter.builder("pulse.score.task.outbox.republish.runs").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${pulse.score-task-outbox.dispatch-delay:5s}")
    public void publishReady() {
        dispatcher.publishReady();
    }
}
