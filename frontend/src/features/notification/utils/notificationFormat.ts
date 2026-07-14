import type { NotificationType } from '../../../shared/api/notificationApi';

/**
 * 서버 알림 종류를 화면에 표시할 한글 문구로 변환합니다.
 */
const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
    GAME_START: '경기 시작',
    SURGE: '모멘텀 급상승',
};

/**
 * 알림 종류의 한글 표시명을 반환합니다.
 */
export function getNotificationTypeLabel(
    type: NotificationType,
    ): string {
    return NOTIFICATION_TYPE_LABEL[type];
}

/**
 * 서버의 ISO 날짜 문자열을 한국 사용자용 날짜 형식으로 변환합니다.
 *
 * 예:
 * 2026-07-13T09:42:59Z
 * → 7월 13일 18:42
 */
export function formatNotificationDate(value: string): string {
    const date = new Date(value);

    /*
    * 잘못된 날짜 문자열이 전달되어도
    * 화면 전체가 깨지지 않도록 원본 값을 반환합니다.
    */
    if (Number.isNaN(date.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat('ko-KR', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
    }).format(date);
}

/**
 * 검색 시 영문 알림 종류와 한글 표시명도 함께 검색할 수 있도록
 * 한 건의 알림에서 검색 대상 문자열을 만듭니다.
 */
export function createNotificationSearchText(
    type: NotificationType,
    message: string,
    ): string {
    return [
        type,
        getNotificationTypeLabel(type),
        message,
    ]
        .join(' ')
        .toLowerCase();
}