package com.pulse.common.message;

import com.pulse.common.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(NotificationEvent event) {
        rabbitTemplate.convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, event);
    }
}
