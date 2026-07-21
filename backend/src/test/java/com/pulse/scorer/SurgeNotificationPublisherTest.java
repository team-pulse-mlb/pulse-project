package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import com.pulse.domain.NotificationEventLogRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SurgeNotificationPublisherTest {

    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final NotificationEventLogRepository notificationEventLogRepository =
            mock(NotificationEventLogRepository.class);
    private final SurgeNotificationPublisher publisher = new SurgeNotificationPublisher(
            new LatestTagSelector(),
            notificationEventPublisher,
            notificationEventLogRepository
    );

    @Test
    void publish_shouldKeepExistingNotificationPayloadContract() {
        Instant occurredAt = Instant.parse("2026-07-08T05:00:00Z");
        List<String> tags = List.of("역전");
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);

        publisher.publish(100L, tags, List.of(), occurredAt);

        verify(notificationEventPublisher).publish(eventCaptor.capture(), org.mockito.ArgumentMatchers.eq(tags));
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(NotificationType.SURGE);
        assertThat(event.gameId()).isEqualTo(100L);
        assertThat(event.message()).isEqualTo("지금 볼 만한 경기가 있어요 — 역전");
        assertThat(event.latestTag()).isEqualTo("역전");
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
        assertThat(event.eventId()).isEqualTo(SurgeNotificationPublisher.eventIdFor(100L, occurredAt));
    }

    @Test
    void publish_shouldSkipAlreadyStoredDeterministicEvent() {
        Instant occurredAt = Instant.parse("2026-07-08T05:00:00Z");
        when(notificationEventLogRepository.existsById(
                SurgeNotificationPublisher.eventIdFor(100L, occurredAt))).thenReturn(true);

        publisher.publish(100L, List.of("역전"), List.of(), occurredAt);

        verify(notificationEventPublisher, never()).publish(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void eventIdFor_shouldUseGameTimeAndTypeAsDeterministicKey() {
        Instant occurredAt = Instant.parse("2026-07-08T05:00:00Z");

        assertThat(SurgeNotificationPublisher.eventIdFor(100L, occurredAt))
                .isEqualTo(SurgeNotificationPublisher.eventIdFor(100L, occurredAt))
                .isNotEqualTo(SurgeNotificationPublisher.eventIdFor(101L, occurredAt))
                .isNotEqualTo(SurgeNotificationPublisher.eventIdFor(100L, occurredAt.plusSeconds(1)));
    }

    @Test
    void publish_shouldUseIndependentTransactionAfterOriginalCommit() throws NoSuchMethodException {
        Transactional transactional = SurgeNotificationPublisher.class
                .getMethod("publish", long.class, List.class, List.class, Instant.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
