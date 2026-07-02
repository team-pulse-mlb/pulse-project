package com.pulse.scorer;

import com.pulse.common.config.RabbitConfig;
import com.pulse.common.messaging.ScoreTask;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * scorer 프로필에서만 동작하는 메시지 소비자.
 * 처리 중 예외가 발생하면 메시지는 재큐잉 없이 DLQ로 이동한다.
 */
@Component
@Profile("scorer")
@RequiredArgsConstructor
public class ScoreTaskListener {

    private final ScoringService scoringService;

    @RabbitListener(queues = RabbitConfig.SCORE_QUEUE)
    public void handle(ScoreTask task) {
        scoringService.recalculate(task.gameId());
    }
}
