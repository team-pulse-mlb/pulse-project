package com.pulse.common.message;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.metrics.PulseMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(ScoreTask task) {
        try {
            rabbitTemplate.convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, task);
        } catch (RuntimeException exception) {
            PulseMetrics.increment("pulse.score.task.publish.failures");
            throw exception;
        }
    }
}
