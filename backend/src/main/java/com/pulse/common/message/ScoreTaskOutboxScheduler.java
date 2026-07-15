package com.pulse.common.message;

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

    @Scheduled(fixedDelayString = "${pulse.score-task-outbox.dispatch-delay:5s}")
    public void publishReady() {
        dispatcher.publishReady();
    }
}
