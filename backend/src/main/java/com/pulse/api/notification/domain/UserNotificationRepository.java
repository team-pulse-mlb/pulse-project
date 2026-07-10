package com.pulse.api.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

/**
 * user_notifications 테이블에 접근하는 Repository입니다.
 *
 * JpaRepository<UserNotification, Long>
 *
 * - UserNotification:
 *   이 Repository가 관리하는 엔티티
 *
 * - Long:
 *   UserNotification의 PK인 id 필드 타입
 */
public interface UserNotificationRepository
        extends JpaRepository<UserNotification, Long> {

    /**
     * 사용자별 알림을 중복 없이 저장합니다.
     *
     * RabbitMQ는 메시지 처리 실패나 ACK 전달 실패 등의 이유로
     * 동일한 메시지를 다시 전달할 수 있습니다.
     *
     * 이때 같은 event_id + user_id 조합이 이미 존재하면
     * PostgreSQL의 ON CONFLICT DO NOTHING을 이용해
     * 오류 없이 저장을 건너뜁니다.
     *
     * 왜 exists 조회 후 save하지 않는가?
     *
     * exists 조회와 INSERT 사이에 다른 요청이 먼저 저장하면
     * 동시성 문제로 UNIQUE 제약 위반이 발생할 수 있습니다.
     *
     * 반면 ON CONFLICT는 중복 확인과 INSERT 처리를
     * DB가 하나의 SQL 안에서 원자적으로 처리합니다.
     *
     * 반환값:
     * - 1: 새로운 알림이 저장됨
     * - 0: 같은 알림이 이미 있어서 저장하지 않음
     *
     * @param eventId  원본 notification_events의 event_id
     * @param userId   알림을 받을 사용자의 user_id
     * @param message  알림함에 표시할 문구
     * @param createdAt 사용자별 알림 생성 시각
     * @return 실제로 INSERT된 행 개수
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO user_notifications (
                        event_id,
                        user_id,
                        message,
                        read_at,
                        created_at
                    )
                    VALUES (
                        :eventId,
                        :userId,
                        :message,
                        NULL,
                        :createdAt
                    )
                    ON CONFLICT (event_id, user_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("userId") Long userId,
            @Param("message") String message,
            @Param("createdAt") Instant createdAt
    );
}