package com.pulse.api.notification.domain;

import com.pulse.api.user.domain.Member;
import com.pulse.domain.NotificationEventLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

/**
 * 사용자 한 명에게 전달된 알림 한 건을 나타내는 엔티티입니다.
 *
 * notification_events:
 * - GAME_START 또는 SURGE가 한 번 발생했다는 원본 사건
 *
 * user_notifications:
 * - 해당 원본 사건을 어떤 사용자에게 전달했는지 나타내는 사용자별 알림
 *
 * 예:
 * GAME_START 이벤트 하나가 발생하고 대상 사용자가 3명이라면
 *
 * notification_events        → 1행
 * user_notifications         → 3행
 */
@Entity
@Table(
        name = "user_notifications",

        /*
         * 동일한 원본 이벤트를 같은 사용자에게 두 번 저장하지 못하게 합니다.
         *
         * RabbitMQ는 처리 실패나 ACK 누락 등이 발생하면
         * 동일 메시지를 다시 전달할 수 있습니다.
         *
         * 따라서 동일한 event_id + user_id 조합이 다시 들어오더라도
         * DB에서 중복 저장을 차단해야 합니다.
         */
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_notifications_event_user",
                        columnNames = {"event_id", "user_id"}
                )
        },

        /*
         * 사용자의 알림 목록을 최신순으로 조회할 때 사용하는 인덱스입니다.
         *
         * 이후 다음과 같은 조회에 도움이 됩니다.
         *
         * WHERE user_id = ?
         * ORDER BY created_at DESC
         */
        indexes = {
                @Index(
                        name = "idx_user_notifications_user_created_at",
                        columnList = "user_id, created_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification {

    /**
     * 사용자별 알림 행의 PK입니다.
     *
     * PostgreSQL의 BIGSERIAL과 대응되도록
     * IDENTITY 전략을 사용합니다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 이 사용자 알림의 원본 알림 사건입니다.
     *
     * 여러 사용자의 UserNotification이
     * 하나의 NotificationEventLog를 참조할 수 있으므로
     * 다대일(ManyToOne) 관계입니다.
     *
     * 예:
     * 하나의 GAME_START 사건
     * → 사용자 A 알림
     * → 사용자 B 알림
     * → 사용자 C 알림
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "event_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_user_notifications_event"
            )
    )
    private NotificationEventLog event;

    /**
     * 이 알림을 받는 사용자입니다.
     *
     * 한 명의 사용자는 여러 알림을 가질 수 있으므로
     * UserNotification 입장에서는 다대일 관계입니다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_user_notifications_user"
            )
    )
    private Member member;

    /**
     * 알림함에 표시할 메시지입니다.
     *
     * 예:
     * - 관심 팀 경기가 시작됐어요 · LAD @ NYY
     * - 경기 흐름이 급변하고 있어요
     *
     * 원본 NotificationEvent의 message 값을
     * 사용자별 알림 생성 시 복사해서 저장합니다.
     */
    @Column(name = "message", columnDefinition = "text")
    private String message;

    /**
     * 사용자가 이 알림을 읽은 시각입니다.
     *
     * null:
     * - 아직 읽지 않은 알림
     *
     * 값이 존재:
     * - 읽은 알림
     */
    @Column(name = "read_at")
    private Instant readAt;

    /**
     * 사용자별 알림이 생성된 시각입니다.
     *
     * 원본 사건 발생 시각인 notification_events.occurred_at과는
     * 의미가 조금 다릅니다.
     *
     * occurred_at:
     * - 경기 시작 또는 SURGE가 실제로 발생한 시각
     *
     * created_at:
     * - Consumer가 이 사용자 알림을 저장한 시각
     */
    @Column(
            name = "created_at",
            updatable = false
    )
    private Instant createdAt;

    /**
     * 사용자별 알림을 생성하는 내부 생성자입니다.
     *
     * 외부에서 new UserNotification(...)을 직접 호출하는 대신
     * 아래 create() 정적 팩토리 메서드를 사용합니다.
     */
    private UserNotification(
            NotificationEventLog event,
            Member member,
            String message
    ) {
        /*
         * event_id와 user_id는 DB에서 NOT NULL이므로
         * Java 객체 생성 단계에서도 null을 허용하지 않습니다.
         */
        this.event = Objects.requireNonNull(
                event,
                "알림 원본 이벤트는 필수입니다."
        );

        this.member = Objects.requireNonNull(
                member,
                "알림 대상 사용자는 필수입니다."
        );

        this.message = message;
    }

    /**
     * 사용자별 알림을 생성합니다.
     *
     * @param event   notification_events에 저장된 원본 알림 사건
     * @param member  알림을 받을 사용자
     * @param message 알림함에 표시할 메시지
     * @return 새 UserNotification 엔티티
     */
    public static UserNotification create(
            NotificationEventLog event,
            Member member,
            String message
    ) {
        return new UserNotification(
                event,
                member,
                message
        );
    }

    /**
     * INSERT 직전에 사용자 알림 생성 시각을 기록합니다.
     *
     * createdAt이 이미 정해져 있지 않은 경우에만
     * 현재 시각을 넣습니다.
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * 알림을 읽음 상태로 변경합니다.
     *
     * 이미 읽은 알림에 읽음 요청이 다시 들어오더라도
     * 최초 읽은 시각을 유지합니다.
     *
     * 이처럼 같은 요청이 반복돼도 결과가 달라지지 않는 성질을
     * 멱등성이라고 합니다.
     *
     * @param readAt 알림을 읽은 시각
     */
    public void markAsRead(Instant readAt) {
        if (this.readAt != null) {
            return;
        }

        this.readAt = Objects.requireNonNull(
                readAt,
                "읽음 시각은 필수입니다."
        );
    }

    /**
     * 현재 알림이 읽음 상태인지 반환합니다.
     *
     * @return readAt이 존재하면 true
     */
    public boolean isRead() {
        return readAt != null;
    }
}