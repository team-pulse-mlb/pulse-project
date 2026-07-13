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
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class NotificationOutboxDispatcherTest {

    private final Instant now = Instant.parse("2026-07-12T00:00:00Z");
    private final NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotificationOutboxProperties properties = new NotificationOutboxProperties(
            Duration.ofSeconds(5),
            Duration.ofMinutes(5),
            50
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

        dispatcher.publishEvent(outbox.getEventId());

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, outbox.toEvent());
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PUBLISHED);
        assertThat(outbox.getPublishedAt()).isEqualTo(now);
    }

    @Test
    void publishEvent_shouldKeepPendingAndScheduleRetryWhenRabbitMqFails() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findPendingByEventIdForUpdate(outbox.getEventId())).thenReturn(Optional.of(outbox));
        doThrow(new AmqpException("브로커 연결 실패"))
                .when(rabbitTemplate).convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, outbox.toEvent());

        dispatcher.publishEvent(outbox.getEventId());

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PENDING);
        assertThat(outbox.getAttemptCount()).isEqualTo(1);
        assertThat(outbox.getNextAttemptAt()).isEqualTo(now.plusSeconds(5));
        assertThat(outbox.getLastError()).isEqualTo("브로커 연결 실패");
    }

    @Test
    void publishReady_shouldRepublishDuePendingEvents() {
        NotificationOutbox outbox = pendingEvent();
        when(repository.findReadyForUpdate(now, org.springframework.data.domain.PageRequest.of(0, 50)))
                .thenReturn(List.of(outbox));

        dispatcher.publishReady();

        verify(rabbitTemplate).convertAndSend(RabbitMqConfig.NOTIFY_EVENTS_QUEUE, outbox.toEvent());
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.STATUS_PUBLISHED);
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
