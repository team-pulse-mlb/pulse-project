package com.pulse.api.notification.domain;

import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 사용자별 알림 INSERT 전용 Repository 확장 인터페이스입니다.
 *
 * JpaRepository의 save()가 아니라 별도 INSERT를 사용하는 이유:
 *
 * 1. event_id + user_id 중복을 DB에서 안전하게 차단해야 함
 * 2. ON CONFLICT DO NOTHING이 필요함
 * 3. 새로 저장된 user_notifications.id를 즉시 받아야 함
 */
public interface UserNotificationInsertRepository {

    /**
     * 사용자 알림을 중복 없이 저장하고,
     * 새로 생성된 알림 ID를 반환합니다.
     *
     * 반환값:
     *
     * OptionalLong.of(id)
     * - 새로운 알림이 저장된 경우
     *
     * OptionalLong.empty()
     * - 같은 event_id + user_id 알림이 이미 존재하는 경우
     *
     * @param eventId 원본 알림 이벤트 ID
     * @param userId 알림 대상 사용자 ID
     * @param message 알림함에 표시할 메시지
     * @param createdAt 사용자별 알림 생성 시각
     * @return 새로 생성된 알림 ID 또는 빈 값
     */
    OptionalLong insertIfAbsentReturningId(
            UUID eventId,
            Long userId,
            String message,
            Instant createdAt
    );
}