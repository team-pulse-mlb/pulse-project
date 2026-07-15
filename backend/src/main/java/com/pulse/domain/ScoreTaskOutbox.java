package com.pulse.domain;

import com.pulse.common.message.ScoreTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** RabbitMQ 발행 전 ScoreTask 원본과 발행 상태를 함께 보관하는 영속 outbox. */
@Entity
@Table(
        name = "score_task_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_score_task_outbox_cycle",
                columnNames = {"game_id", "observed_at"}
        )
)
@Getter
@NoArgsConstructor
public class ScoreTaskOutbox {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    @Id
    @Column(name = "outbox_id")
    private UUID outboxId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private ScoreTask payload;

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

    public static ScoreTaskOutbox pending(ScoreTask task, Instant createdAt) {
        ScoreTaskOutbox outbox = new ScoreTaskOutbox();
        outbox.outboxId = UUID.randomUUID();
        outbox.gameId = task.gameId();
        outbox.observedAt = task.observedAt();
        outbox.payload = task;
        outbox.status = STATUS_PENDING;
        outbox.nextAttemptAt = createdAt;
        outbox.createdAt = createdAt;
        return outbox;
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
