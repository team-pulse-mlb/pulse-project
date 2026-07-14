import type { NotificationSummary } from '../../../shared/api/notificationApi';
import NotificationItem from './NotificationItem';

interface NotificationListProps {
    /** 표시할 알림 목록 */
    notifications: NotificationSummary[];

    /** 알림 한 건을 클릭했을 때 실행할 함수 */
    onNotificationClick: (
        notification: NotificationSummary,
    ) => void;

    /** 읽음 처리 중 중복 클릭을 막기 위한 상태 */
    disabled?: boolean;

    /** 헤더 드롭다운용 작은 화면 여부 */
    compact?: boolean;
}

/**
 * 알림 배열을 반복해서 출력하는 목록 컴포넌트입니다.
 *
 * 빈 상태, 로딩, 오류 화면은 사용하는 위치마다 모양이 다르므로
 * 이 컴포넌트에서는 처리하지 않습니다.
 *
 * NotificationsPage와 NotificationDropdown이
 * 각자 상황에 맞는 빈 상태 화면을 결정합니다.
 */
function NotificationList({
    notifications,
    onNotificationClick,
    disabled = false,
    compact = false,
    }: NotificationListProps) {
    return (
        <ul className="overflow-hidden rounded-panel border border-[#E3E7ED] bg-white shadow-sm">
        {notifications.map((notification) => (
            <NotificationItem
            key={notification.notificationId}
            notification={notification}
            onClick={onNotificationClick}
            disabled={disabled}
            compact={compact}
            />
        ))}
        </ul>
    );
}

export default NotificationList;