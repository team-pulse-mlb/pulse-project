import type { NotificationSummary } from '../../../shared/api/notificationApi';
import {
    formatNotificationDate,
    getNotificationTypeLabel,
} from '../utils/notificationFormat';

interface NotificationItemProps {
    /** 화면에 표시할 알림 한 건 */
    notification: NotificationSummary;

    /**
     * 알림을 클릭했을 때 실행할 함수입니다.
     *
     * 실제 읽음 API 호출과 경기 상세 이동은
     * 페이지 또는 드롭다운 상위 컴포넌트에서 담당합니다.
     */
    onClick: (notification: NotificationSummary) => void;

    /** 읽음 처리 중 중복 클릭을 막기 위한 상태 */
    disabled?: boolean;

    /**
     * true이면 헤더 드롭다운에 맞는 작은 크기로 표시합니다.
     * false이면 전체 알림 페이지 크기로 표시합니다.
     */
    compact?: boolean;
}

/**
 * 알림 한 건을 표시하는 공통 컴포넌트입니다.
 *
 * 이 컴포넌트는 API를 직접 호출하지 않습니다.
 * 전달받은 알림 데이터를 표시하고 클릭 이벤트만 상위로 전달합니다.
 *
 * 이렇게 분리하면:
 * - 전체 알림 페이지
 * - 헤더 알림 드롭다운
 *
 * 두 화면에서 같은 UI를 재사용할 수 있습니다.
 */
function NotificationItem({
    notification,
    onClick,
    disabled = false,
    compact = false,
    }: NotificationItemProps) {
    const isUnread = !notification.read;

    return (
        <li className="border-b border-[#EDF0F4] last:border-b-0">
        <button
            type="button"
            disabled={disabled}
            onClick={() => onClick(notification)}
            className={`flex w-full items-start gap-3 text-left transition-colors hover:bg-[#F7F8FA] disabled:cursor-wait ${
            compact ? 'px-4 py-3' : 'px-5 py-4'
            } ${isUnread ? 'bg-[#F7FAFF]' : 'bg-white'}`}
        >
            {/*
            * 미읽음 알림은 빨간색 점,
            * 읽은 알림은 테두리만 있는 원으로 표시합니다.
            */}
            <span
            aria-label={isUnread ? '읽지 않은 알림' : '읽은 알림'}
            className={`mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${
                isUnread
                ? 'bg-mlb-red'
                : 'border border-[#AAB3C0] bg-white'
            }`}
            />

            <span className="min-w-0 flex-1">
            <span className="flex items-center justify-between gap-3">
                {/* GAME_START, SURGE를 사용자용 한글 문구로 표시합니다. */}
                <span
                className={`text-xs font-semibold ${
                    isUnread ? 'text-mlb-red' : 'text-text-muted'
                }`}
                >
                {getNotificationTypeLabel(notification.type)}
                </span>

                <time
                dateTime={notification.createdAt}
                className="shrink-0 text-xs text-text-muted"
                >
                {formatNotificationDate(notification.createdAt)}
                </time>
            </span>

            {/*
            * 백엔드가 완성해서 내려준 message를 그대로 표시합니다.
            * 팀 이름, 점수, 스포일러 문구를 프론트에서 다시 조합하지 않습니다.
            */}
            <span
                className={`${compact ? 'mt-1 line-clamp-2' : 'mt-1.5'} block text-sm leading-6 ${
                isUnread
                    ? 'font-semibold text-ink'
                    : 'font-normal text-text-muted'
                }`}
            >
                {notification.message}
            </span>
            </span>

            {/* 알림 클릭 시 경기 상세 페이지로 이동한다는 표시 */}
            <span
            aria-hidden="true"
            className={`${compact ? 'mt-4' : 'mt-5'} shrink-0 text-lg text-[#AAB3C0]`}
            >
            ›
            </span>
        </button>
        </li>
    );
}

export default NotificationItem;