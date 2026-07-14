package com.pulse.api.notification.dto;

import jakarta.validation.constraints.Positive;

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

        /*
         * 알림 PK는 1 이상의 양수여야 합니다.
         *
         * List 내부 원소에 @Positive를 붙였기 때문에
         * [1, 2, 0]처럼 0 이하 ID가 포함되면 400 응답이 발생합니다.
         */
        List<
                @Positive(
                        message = "알림 ID는 1 이상의 값이어야 합니다."
                )
                        Long
                > notificationIds
) {
}