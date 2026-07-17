package com.pulse.scorer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulse.common.message.NotificationEvent;
import com.pulse.common.message.NotificationEvent.NotificationType;
import com.pulse.common.message.NotificationEventPublisher;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class SurgeNotificationPublisherTest {

    private final LiveSignalPublisher liveSignalPublisher = mock(LiveSignalPublisher.class);
    private final NotificationEventPublisher notificationEventPublisher = mock(NotificationEventPublisher.class);
    private final SurgeNotificationPublisher publisher = new SurgeNotificationPublisher(
            liveSignalPublisher,
            notificationEventPublisher
    );

    @Test
    void publish_shouldKeepExistingNotificationPayloadContract() {
        Instant occurredAt = Instant.parse("2026-07-08T05:00:00Z");
        List<String> tags = List.of("역전");
        when(liveSignalPublisher.resolveLatestTag(100L, tags, List.of(), occurredAt))
                .thenReturn("역전");
        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);

        publisher.publish(100L, tags, List.of(), occurredAt);

        verify(notificationEventPublisher).publish(eventCaptor.capture(), org.mockito.ArgumentMatchers.eq(tags));
        NotificationEvent event = eventCaptor.getValue();
        assertThat(event.type()).isEqualTo(NotificationType.SURGE);
        assertThat(event.gameId()).isEqualTo(100L);
        assertThat(event.message()).isEqualTo("지금 볼 만한 경기가 있어요 — 역전");
        assertThat(event.latestTag()).isEqualTo("역전");
        assertThat(event.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void publish_shouldUseIndependentTransactionAfterOriginalCommit() throws NoSuchMethodException {
        Transactional transactional = SurgeNotificationPublisher.class
                .getMethod("publish", long.class, List.class, List.class, Instant.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
