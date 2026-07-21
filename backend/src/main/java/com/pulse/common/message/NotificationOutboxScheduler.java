package com.pulse.common.message;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** poller 또는 game processor 역할에서 알림 outbox 재발행을 주기적으로 요청한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("(${pulse.poller.enabled:false} or ${pulse.scorer.enabled:true})"
        + " and ${pulse.notification-outbox.scheduler-enabled:true}")
public class NotificationOutboxScheduler {

    private final NotificationOutboxDispatcher dispatcher;

    @Scheduled(fixedDelayString = "${pulse.notification-outbox.dispatch-delay:5s}")
    public void publishReady() {
        dispatcher.publishReady();
    }
}
