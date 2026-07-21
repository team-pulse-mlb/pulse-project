package com.pulse.gameprocessing.consumer;

import com.pulse.gameprocessing.application.GameFinalizationService;
import com.pulse.gameprocessing.application.LiveScoringService;
import com.pulse.gameprocessing.application.PregameScoringService;
import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.message.ScoreTask;
import com.pulse.common.message.ScoreTaskFactory;
import com.pulse.common.metrics.PulseMetrics;
import com.pulse.domain.GameLifecycle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulse.game-processor", name = "enabled", havingValue = "true")
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
        String taskType = taskType(lifecycleState);
        PulseMetrics.increment("pulse.score.task.consumed", "type", taskType);
        PulseMetrics.record("pulse.score.task.processing", () -> route(task, lifecycleState), "type", taskType);
    }

    private void route(ScoreTask task, String lifecycleState) {
        if (ScoreTaskFactory.PREGAME_LIFECYCLE.equals(lifecycleState)) {
            pregameScoringService.handle(task);
        } else if (isTerminal(lifecycleState)) {
            gameFinalizationService.handle(task);
        } else {
            liveScoringService.handle(task);
        }
    }

    private static String taskType(String lifecycleState) {
        if (ScoreTaskFactory.PREGAME_LIFECYCLE.equals(lifecycleState)) {
            return "pregame";
        }
        return isTerminal(lifecycleState) ? "terminal" : "live";
    }

    private static boolean isTerminal(String lifecycleState) {
        return GameLifecycle.FINAL.name().equals(lifecycleState)
                || GameLifecycle.DONE.name().equals(lifecycleState)
                || GameLifecycle.SUSPENDED_POSTPONED.name().equals(lifecycleState);
    }
}
