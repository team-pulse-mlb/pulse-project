package com.pulse.api.notification.dto;

import com.pulse.domain.NotificationEventLog;
import com.pulse.api.notification.domain.UserNotification;

import java.time.Instant;

/**
 * 알림함 목록에서 프론트엔드로 전달하는 단일 알림 응답 DTO입니다.
 *
 * 엔티티를 그대로 반환하지 않고 필요한 정보만 추려서 전달합니다.
 *
 * record를 사용한 이유:
 * 1. 응답 DTO는 생성된 이후 값이 변경될 필요가 없음
 * 2. getter, 생성자, equals, hashCode를 자동 생성
 * 3. Java 21 환경에서 간결하고 안전하게 사용할 수 있음
 *
 * @param notificationId 사용자 알림의 고유 ID
 * @param type 알림 종류: GAME_START 또는 SURGE
 * @param gameId 알림과 연결된 경기 ID
 * @param message 사용자에게 보여줄 알림 문구
 * @param read 읽음 여부
 * @param readAt 읽은 시각, 읽지 않았다면 null
 * @param createdAt 알림 생성 시각
 */
public record NotificationResponse(
        Long notificationId,
        String type,
        Long gameId,
        String message,
        boolean read,
        Instant readAt,
        Instant createdAt
) {

    /**
     * UserNotification 엔티티를 API 응답 DTO로 변환합니다.
     *
     * UserNotification에는 사용자별 정보가 저장되어 있고,
     * NotificationEventLog에는 알림 종류와 경기 ID가 저장되어 있습니다.
     *
     * 따라서 두 엔티티의 값을 조합하여 하나의 응답으로 만듭니다.
     *
     * @param notification 변환할 사용자 알림 엔티티
     * @return 프론트엔드에 전달할 알림 응답
     */
    public static NotificationResponse from(
            UserNotification notification
    ) {
        /*
         * 알림 원본 이벤트입니다.
         *
         * Repository의 @EntityGraph(attributePaths = "event") 덕분에
         * 알림 목록 조회 시 event도 함께 조회됩니다.
         */
        NotificationEventLog event = notification.getEvent();

        return new NotificationResponse(
                notification.getId(),
                event.getType(),
                event.getGameId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}