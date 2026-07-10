package com.pulse.common.message;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.common.message.NotificationEvent.NotificationType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class PublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

    @Test
    void scoreTaskPublisher_shouldSendToScoreTasksQueue() {
        ScoreTask task = new ScoreTask(1L, Instant.parse("2026-07-08T00:00:00Z"), null, "LIVE", null);

        new ScoreTaskPublisher(rabbitTemplate).publish(task);

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.SCORE_TASKS_QUEUE, task);
    }

    @Test
    void notificationEventPublisher_shouldSendToNotifyEventsQueue() {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationType.GAME_START,
                1L,
                "관심 팀 경기가 시작됐어요 — BOS @ NYY",
                null,
                Instant.parse("2026-07-08T00:00:00Z")
        );

        new NotificationEventPublisher(rabbitTemplate, new AfterCommitExecutor()).publish(event);

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, event);
    }
}
