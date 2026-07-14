package com.pulse.api.notification.dto;

/**
 * 알림 읽음 처리 결과 DTO입니다.
 *
 * @param updatedCount 실제로 읽음 상태로 변경된 알림 개수
 */
public record NotificationReadResponse(
        int updatedCount
) {
}