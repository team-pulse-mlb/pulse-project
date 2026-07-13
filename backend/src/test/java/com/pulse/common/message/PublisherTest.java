package com.pulse.common.message;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.domain.NotificationEventLog;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.NotificationOutbox;
import com.pulse.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
    void notificationEventPublisher_shouldPersistEventAndOutboxBeforeDispatch() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationType.GAME_START,
                1L,
                "관심 팀 경기가 시작됐어요 — BOS @ NYY",
                null,
                now
        );
        NotificationEventLogRepository eventLogRepository = mock(NotificationEventLogRepository.class);
        NotificationOutboxRepository outboxRepository = mock(NotificationOutboxRepository.class);
        NotificationOutboxDispatcher dispatcher = mock(NotificationOutboxDispatcher.class);

        new NotificationEventPublisher(
                eventLogRepository,
                outboxRepository,
                dispatcher,
                new AfterCommitExecutor(),
                Clock.fixed(now, ZoneOffset.UTC)
        ).publish(event);

        ArgumentCaptor<NotificationEventLog> eventLogCaptor = ArgumentCaptor.forClass(NotificationEventLog.class);
        ArgumentCaptor<NotificationOutbox> outboxCaptor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(eventLogRepository).save(eventLogCaptor.capture());
        verify(outboxRepository).save(outboxCaptor.capture());
        verify(dispatcher).publishEvent(eventId);
        assertThat(eventLogCaptor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(outboxCaptor.getValue().getEventId()).isEqualTo(eventId);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);
    }
}
