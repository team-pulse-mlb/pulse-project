package com.pulse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 알림 발화 이력. scorer가 SURGE·GAME_START를 발행하면서 함께 영속한다.
 * 사용자별 배달(user_notifications)은 알림 소비자(윤호) 영역이다.
 */
@Entity
@Table(
        name = "notification_events",
        indexes = @Index(name = "idx_notification_events_game_occurred_at", columnList = "game_id, occurred_at")
)
@Getter
@Setter
@NoArgsConstructor
public class NotificationEventLog {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    private String type;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags;

    @Column(name = "occurred_at")
    private Instant occurredAt;
}
