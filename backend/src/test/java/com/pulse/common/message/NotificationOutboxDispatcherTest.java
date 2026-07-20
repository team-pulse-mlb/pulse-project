package com.pulse.common.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.config.RabbitMqConfig;
import com.pulse.domain.NotificationOutbox;
import com.pulse.domain.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class NotificationOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-07-12T00:00:00Z");
    private final NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificationOutboxProperties properties = new NotificationOutboxProperties(
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            50,
            Duration.ofSeconds(1),
            Duration.ofDays(7),
            500
    );
    private final NotificationOutboxDispatcher dispatcher = new NotificationOutboxDispatcher(
            repository,
            rabbitTemplate,
            properties,
            Clock.fixed(now, ZoneOffset.UTC)
    );

    @Test
    void publishEvent_shouldMarkPublishedWhenRabbitMqAcceptsMessage() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        completeConfirmWithAck(outbox);

        dispatcher.publishEvent(outbox.getEventId());

        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("브로커 ack를 받기 전에는 알림 outbox를 발행 완료로 바꾸지 않는다")
    void publishEvent_shouldMarkPublishedOnlyAfterBrokerAck() throws Exception {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        AtomicReference<CorrelationData> sentCorrelation = new AtomicReference<>();
        CountDownLatch sent = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            sentCorrelation.set(invocation.getArgument(2));
            sent.countDown();
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );

        CompletableFuture<Void> publishing = CompletableFuture.runAsync(
                () -> dispatcher.publishEvent(outbox.getEventId())
        );

        assertThat(sent.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);

        sentCorrelation.get().getFuture().complete(new CorrelationData.Confirm(true, null));
        publishing.get(1, TimeUnit.SECONDS);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
    }

    @Test
    void publishEvent_shouldKeepPendingAndScheduleRetryWhenRabbitMqFails() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 연결 실패"))
                .when(rabbitTemplate).convertAndSend(
                        org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                        org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                        org.mockito.ArgumentMatchers.any(CorrelationData.class)
                );

        dispatcher.publishEvent(outbox.getEventId());

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("브로커 연결 실패");
    }

    @Test
    @DisplayName("브로커 nack이면 알림 outbox를 대기 상태로 유지하고 재시도를 예약한다")
    void publishEvent_shouldKeepPendingAndScheduleRetryWhenBrokerNacks() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(2);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "큐 저장 실패"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );

        dispatcher.publishEvent(outbox.getEventId());

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).contains("큐 저장 실패");
    }

    @Test
    @DisplayName("publisher confirm 시간이 초과되면 알림 outbox를 대기 상태로 유지한다")
    void publishEvent_shouldKeepPendingAndScheduleRetryWhenConfirmTimesOut() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        NotificationOutboxProperties shortTimeoutProperties = new NotificationOutboxProperties(
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                50,
                Duration.ofMillis(5),
                Duration.ofDays(7),
                500
        );

        new NotificationOutboxDispatcher(
                repository,
                rabbitTemplate,
                shortTimeoutProperties,
                Clock.fixed(now, ZoneOffset.UTC)
        ).publishEvent(outbox.getEventId());

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("TimeoutException");
    }

    @Test
    void publishReady_shouldRepublishDuePendingEvents() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findReadyForUpdate(now, org.springframework.data.domain.PageRequest.of(0, 50)))
                .thenReturn(List.of(outbox));
        completeConfirmWithAck(outbox);

        dispatcher.publishReady();

        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PUBLISHED);
    }

    private void completeConfirmWithAck(NotificationOutbox outbox) {
        org.mockito.Mockito.doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(2);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(RabbitMqConfig.NOTIFY_EVENTS_QUEUE),
                org.mockito.ArgumentMatchers.eq(outbox.toEvent()),
                org.mockito.ArgumentMatchers.any(CorrelationData.class)
        );
    }

    private NotificationOutbox pendingEvent() {
        return NotificationOutbox.pending(new NotificationEvent(
                UUID.randomUUID(),
                NotificationEvent.NotificationType.SURGE,
                100L,
                "지금 볼 만한 경기가 있어요 — 흐름 급변",
                "흐름 급변",
                now.minusSeconds(10)
        ), now.minusSeconds(5));
    }
}
