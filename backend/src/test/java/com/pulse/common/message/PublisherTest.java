package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.transaction.AfterCommitExecutor;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.domain.NotificationEventLog;
import com.pulse.domain.NotificationEventLogRepository;
import com.pulse.domain.NotificationOutbox;
import com.pulse.domain.NotificationOutboxRepository;
import com.pulse.domain.ScoreTaskOutbox;
import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublisherTest {

    @Test
    void scoreTaskPublisher_shouldPersistTaskAndPendingStateBeforeDispatch() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        ScoreTask task = new ScoreTask(1L, now, null, "LIVE", null);
        ScoreTaskOutboxRepository outboxRepository = mock(ScoreTaskOutboxRepository.class);
        ScoreTaskOutboxDispatcher dispatcher = mock(ScoreTaskOutboxDispatcher.class);
        when(outboxRepository.findByGameIdAndObservedAt(1L, now)).thenReturn(Optional.empty());
        when(outboxRepository.save(any(ScoreTaskOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new ScoreTaskPublisher(
                outboxRepository,
                dispatcher,
                new AfterCommitExecutor(),
                Clock.fixed(now, ZoneOffset.UTC)
        ).publish(task);

        ArgumentCaptor<ScoreTaskOutbox> outboxCaptor = ArgumentCaptor.forClass(ScoreTaskOutbox.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        verify(dispatcher).publishTask(outboxCaptor.getValue().getOutboxId());
        assertThat(outboxCaptor.getValue().getPayload()).isEqualTo(task);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outboxCaptor.getValue().getNextAttemptAt()).isEqualTo(now);
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
