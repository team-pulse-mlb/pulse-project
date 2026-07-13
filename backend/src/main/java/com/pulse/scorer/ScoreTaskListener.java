package com.pulse.scorer;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.message.ScoreTask;
import com.pulse.poller.GameLifecycle;
import com.pulse.poller.ScoreTaskFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.scorer", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ScoreTaskListener {

    private final LiveScoringService liveScoringService;
    private final PregameScoringService pregameScoringService;
    private final GameFinalizationService gameFinalizationService;

    @RabbitListener(queues = RabbitMqConfig.SCORE_TASKS_QUEUE)
    public void handle(ScoreTask task) {
        if (task == null) {
            log.debug("빈 ScoreTask skip");
            return;
        }

        String lifecycleState = task.lifecycleState();
        if (ScoreTaskFactory.PREGAME_LIFECYCLE.equals(lifecycleState)) {
            pregameScoringService.handle(task);
            return;
        }
        if (isTerminal(lifecycleState)) {
            gameFinalizationService.handle(task);
            return;
        }
        liveScoringService.handle(task);
    }

    private static boolean isTerminal(String lifecycleState) {
        return GameLifecycle.FINAL.name().equals(lifecycleState)
                || GameLifecycle.DONE.name().equals(lifecycleState)
                || GameLifecycle.SUSPENDED_POSTPONED.name().equals(lifecycleState);
    }
}
