package com.pulse.poller;

import com.pulse.common.config.RabbitConfig;
import com.pulse.common.messaging.ScoreTask;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(long gameId) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.SCORE_ROUTING_KEY, new ScoreTask(gameId));
    }
}
