package com.pulse.api.notification.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;

/**
 * 알림 읽음 처리 요청 DTO입니다.
 *
 * 확정 API:
 * POST /api/me/notifications/read
 *
 * 사용 방법:
 *
 * 1. 선택한 알림만 읽음 처리
 *
 * {
 *   "all": false,
 *   "notificationIds": [1, 2, 3]
 * }
 *
 * 2. 현재 사용자의 모든 알림 읽음 처리
 *
 * {
 *   "all": true
 * }
 *
 * all이 true이면 notificationIds 값은 사용하지 않습니다.
 *
 * @param all 모든 미읽음 알림을 읽음 처리할지 여부
 * @param notificationIds 선택해서 읽음 처리할 알림 ID 목록
 */
public record NotificationReadRequest(

        boolean all,

        List<Long> notificationIds
) {

    /**
     * 알림 ID를 사용하는 요청일 때만 ID 값을 검증합니다.
     *
     * all=true:
     * - 전체 읽음 요청이므로 notificationIds를 사용하지 않습니다.
     * - 값이 포함되어 있더라도 ID 검증을 수행하지 않습니다.
     *
     * all=false:
     * - 선택 읽음 요청이므로 전달된 숫자는 1 이상이어야 합니다.
     * - null 또는 빈 목록은 기존 Service 정책에 따라 0건 처리됩니다.
     * - 목록 안의 null 값도 기존 Service에서 제거하므로 허용합니다.
     *
     * 기존 List<@Positive Long> 검증과 동일하게
     * null은 허용하고 0 이하 숫자만 거부합니다.
     */
    @JsonIgnore
    @AssertTrue(
            message = "알림 ID는 1 이상의 값이어야 합니다."
    )
    public boolean isNotificationIdsValid() {
        if (all
                || notificationIds == null
                || notificationIds.isEmpty()) {
            return true;
        }

        return notificationIds.stream()
                .allMatch(
                        notificationId ->
                                notificationId == null
                                        || notificationId > 0
                );
    }
}