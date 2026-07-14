import { Link } from 'react-router';

import type { NotificationSummary } from '../../../shared/api/notificationApi';
import NotificationList from './NotificationList';

interface NotificationDropdownProps {
    /** 드롭다운에 표시할 최근 알림 목록 */
    notifications: NotificationSummary[];

    /** 한 건이라도 미읽음 알림이 존재하는지 여부 */
    hasUnread: boolean;

    /** 알림 목록 조회 중 여부 */
    isLoading: boolean;

    /** 알림 목록 조회 실패 여부 */
    isError: boolean;

    /** 선택 알림 읽음 처리 중 여부 */
    isMarkingSelected: boolean;

    /** 모든 알림 읽음 처리 중 여부 */
    isMarkingAll: boolean;

    /** 읽음 처리 도중 발생한 사용자 안내 오류 */
    actionError: string | null;

    /** 알림 한 건 클릭 함수 */
    onNotificationClick: (
        notification: NotificationSummary,
    ) => void;

    /** 모든 알림 읽음 처리 함수 */
    onMarkAllAsRead: () => void;

    /** 목록 재조회 함수 */
    onRetry: () => void;

    /** 드롭다운 닫기 함수 */
    onClose: () => void;
}

/**
 * 헤더 종 아이콘 아래에 표시되는 알림 드롭다운입니다.
 *
 * 담당 기능:
 * - 최근 알림 최대 5개 표시
 * - 알림 한 건 클릭
 * - 모두 읽음
 * - 전체 알림 페이지 이동
 *
 * 실제 API 호출과 상태 처리는 NotificationBell과
 * useNotifications Hook이 담당합니다.
 */
function NotificationDropdown({
    notifications,
    hasUnread,
    isLoading,
    isError,
    isMarkingSelected,
    isMarkingAll,
    actionError,
    onNotificationClick,
    onMarkAllAsRead,
    onRetry,
    onClose,
    }: NotificationDropdownProps) {
    return (
        <div
        role="dialog"
        aria-label="최근 알림"
        className="absolute right-0 top-[calc(100%+10px)] z-50 w-[min(380px,calc(100vw-24px))] overflow-hidden rounded-panel border border-[#DDE2EA] bg-white shadow-modal"
        >
        {/* 드롭다운 상단 제목 */}
        <div className="flex items-center justify-between border-b border-[#EDF0F4] px-4 py-3.5">
            <div>
            <h2 className="text-sm font-bold text-ink">
                알림
            </h2>

            <p className="mt-0.5 text-xs text-text-muted">
                최근 받은 알림입니다.
            </p>
            </div>

            <button
            type="button"
            onClick={onMarkAllAsRead}
            disabled={!hasUnread || isMarkingAll}
            className="rounded-[8px] px-2.5 py-1.5 text-xs font-semibold text-ink transition-colors hover:bg-[#F3F5F8] disabled:cursor-not-allowed disabled:opacity-40"
            >
            {isMarkingAll ? '처리 중...' : '모두 읽음'}
            </button>
        </div>

        {/* 읽음 처리 실패 안내 */}
        {actionError && (
            <div
            role="alert"
            className="border-b border-red-100 bg-red-50 px-4 py-2.5 text-xs leading-5 text-red-700"
            >
            {actionError}
            </div>
        )}

        {/* 알림 조회 중 */}
        {isLoading && (
            <div className="px-5 py-10 text-center text-sm text-text-muted">
            알림을 불러오는 중입니다.
            </div>
        )}

        {/* 알림 조회 실패 */}
        {isError && (
            <div className="px-5 py-8 text-center">
            <p className="text-sm text-red-700">
                알림을 불러오지 못했습니다.
            </p>

            <button
                type="button"
                onClick={onRetry}
                className="mt-3 rounded-[8px] bg-ink px-3 py-1.5 text-xs font-semibold text-white"
            >
                다시 시도
            </button>
            </div>
        )}

        {/* 알림 없음 */}
        {!isLoading &&
            !isError &&
            notifications.length === 0 && (
            <div className="px-5 py-10 text-center">
                <p className="text-sm font-semibold text-ink">
                새로운 알림이 없습니다.
                </p>

                <p className="mt-1 text-xs text-text-muted">
                경기 시작이나 중요 순간이 발생하면 알려드릴게요.
                </p>
            </div>
            )}

        {/* 최근 알림 목록 */}
        {!isLoading &&
            !isError &&
            notifications.length > 0 && (
            <div className="max-h-[390px] overflow-y-auto">
                <NotificationList
                notifications={notifications}
                onNotificationClick={onNotificationClick}
                disabled={isMarkingSelected}
                compact
                />
            </div>
            )}

        {/* 전체 알림 페이지 이동 */}
        <div className="border-t border-[#EDF0F4] bg-[#FAFBFC] p-2">
            <Link
            to="/notifications"
            onClick={onClose}
            className="flex w-full items-center justify-center rounded-[8px] px-3 py-2.5 text-sm font-semibold text-ink transition-colors hover:bg-[#EFF2F6]"
            >
            전체 알림 보기
            <span
                aria-hidden="true"
                className="ml-1 text-base text-text-muted"
            >
                ›
            </span>
            </Link>
        </div>
        </div>
    );
}

export default NotificationDropdown;