package com.pulse.common.message;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.transaction.AfterCommitExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    public void publish(NotificationEvent event) {
        afterCommitExecutor.execute(() ->
                rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, event));
    }
}
