package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
import com.pulse.domain.ScoreTaskOutboxInsertRepository;
import com.pulse.domain.ScoreTaskOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PublisherTest {

    @Test
    void scoreTaskPublisher_shouldPersistTaskAndPendingStateBeforeDispatch() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        ScoreTask task = new ScoreTask(1L, now, null, "LIVE", null);
        ScoreTaskOutboxRepository outboxRepository = mock(ScoreTaskOutboxRepository.class);
        ScoreTaskOutboxInsertRepository outboxInsertRepository = mock(ScoreTaskOutboxInsertRepository.class);
        ScoreTaskOutboxDispatcher dispatcher = mock(ScoreTaskOutboxDispatcher.class);
        when(outboxRepository.findByGameIdAndObservedAt(1L, now)).thenReturn(Optional.empty());
        when(outboxInsertRepository.insertPending(any(ScoreTaskOutbox.class))).thenReturn(true);

        new ScoreTaskPublisher(
                outboxRepository,
                outboxInsertRepository,
                dispatcher,
                new AfterCommitExecutor(),
                Clock.fixed(now, ZoneOffset.UTC)
        ).publish(task);

        ArgumentCaptor<ScoreTaskOutbox> outboxCaptor = ArgumentCaptor.forClass(ScoreTaskOutbox.class);
        verify(outboxInsertRepository).insertPending(outboxCaptor.capture());
        verify(dispatcher).publishTask(outboxCaptor.getValue().getOutboxId());
        assertThat(outboxCaptor.getValue().getPayload()).isEqualTo(task);
        assertThat(outboxCaptor.getValue().getStatus()).isEqualTo(ScoreTaskOutbox.STATUS_PENDING);
        assertThat(outboxCaptor.getValue().getNextAttemptAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("동시 발행이 insert를 먼저 선점하면 기존 ScoreTask outbox를 재사용한다")
    void scoreTaskPublisher_shouldReuseExistingOutboxWhenConcurrentInsertWins() {
        Instant now = Instant.parse("2026-07-08T00:00:00Z");
        ScoreTask task = new ScoreTask(1L, now, null, "LIVE", null);
        ScoreTaskOutbox existing = ScoreTaskOutbox.pending(task, now.minusSeconds(1));
        ScoreTaskOutboxRepository outboxRepository = mock(ScoreTaskOutboxRepository.class);
        ScoreTaskOutboxInsertRepository outboxInsertRepository = mock(ScoreTaskOutboxInsertRepository.class);
        ScoreTaskOutboxDispatcher dispatcher = mock(ScoreTaskOutboxDispatcher.class);
        when(outboxRepository.findByGameIdAndObservedAt(1L, now))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(outboxInsertRepository.insertPending(any(ScoreTaskOutbox.class))).thenReturn(false);

        new ScoreTaskPublisher(
                outboxRepository,
                outboxInsertRepository,
                dispatcher,
                new AfterCommitExecutor(),
                Clock.fixed(now, ZoneOffset.UTC)
        ).publish(task);

        verify(dispatcher).publishTask(existing.getOutboxId());
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

    @Test
    @DisplayName("즉시 발행이 실패해도 예외를 호출자에게 전파하지 않는다 — outbox 재발행으로 복구")
    void notificationEventPublisher_shouldNotPropagateDispatchFailure() {
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
        doThrow(new IllegalStateException("dispatch failed")).when(dispatcher).publishEvent(eventId);

        NotificationEventPublisher publisher = new NotificationEventPublisher(
                eventLogRepository,
                outboxRepository,
                dispatcher,
                new AfterCommitExecutor(),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        verify(outboxRepository).save(any(NotificationOutbox.class));
    }
}
