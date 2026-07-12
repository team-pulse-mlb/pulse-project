package com.pulse.domain;

import com.pulse.common.message.NotificationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** RabbitMQ 발행 전 알림 이벤트를 보관하는 영속 outbox. */
@Entity
@Table(name = "notification_outbox")
@Getter
@NoArgsConstructor
public class NotificationOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @Column(name = "outbox_id")
    private UUID outboxId;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false)
    private String message;

    @Column(name = "latest_tag")
    private String latestTag;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error")
    private String lastError;

    public static NotificationOutbox pending(NotificationEvent event, Instant createdAt) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.outboxId = UUID.randomUUID();
        outbox.eventId = event.eventId();
        outbox.eventType = event.type().name();
        outbox.gameId = event.gameId();
        outbox.message = event.message();
        outbox.latestTag = event.latestTag();
        outbox.occurredAt = event.occurredAt();
        outbox.status = STATUS_PENDING;
        outbox.nextAttemptAt = createdAt;
        outbox.createdAt = createdAt;
        return outbox;
    }

    public NotificationEvent toEvent() {
        return new NotificationEvent(
                eventId,
                NotificationEvent.NotificationType.valueOf(eventType),
                gameId,
                message,
                latestTag,
                occurredAt
        );
    }

    public void markPublished(Instant publishedAt) {
        this.status = STATUS_PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void recordFailure(Instant nextAttemptAt, String error) {
        this.attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = error;
    }
}
