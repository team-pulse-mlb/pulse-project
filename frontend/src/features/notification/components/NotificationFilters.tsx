import type { NotificationType } from '../../../shared/api/notificationApi';

/**
 * 알림의 읽음 상태 필터입니다.
 */
export type NotificationReadFilter = 'ALL' | 'UNREAD' | 'READ';

/**
 * 알림 종류 필터입니다.
 *
 * ALL:
 * 모든 알림
 *
 * GAME_START:
 * 관심 팀 경기 시작 알림
 *
 * SURGE:
 * 모멘텀 급상승 알림
 */
export type NotificationTypeFilter = 'ALL' | NotificationType;

interface NotificationFiltersProps {
    /** 현재 검색어 */
    keyword: string;

    /** 검색어 변경 함수 */
    onKeywordChange: (keyword: string) => void;

    /** 현재 읽음 상태 필터 */
    readFilter: NotificationReadFilter;

    /** 읽음 상태 필터 변경 함수 */
    onReadFilterChange: (filter: NotificationReadFilter) => void;

    /** 현재 알림 종류 필터 */
    typeFilter: NotificationTypeFilter;

    /** 알림 종류 필터 변경 함수 */
    onTypeFilterChange: (filter: NotificationTypeFilter) => void;

    /** 미읽음 알림 존재 여부 */
    hasUnread: boolean;

    /** 전체 읽음 처리 진행 여부 */
    isMarkingAll: boolean;

    /** 모두 읽음 버튼 클릭 함수 */
    onMarkAllAsRead: () => void;
}

/**
 * 알림 페이지의 검색·필터·전체 읽음 영역입니다.
 *
 * 이 컴포넌트는 실제 알림 데이터를 직접 조회하지 않습니다.
 * 현재 필터 값과 변경 함수만 전달받아 화면을 담당합니다.
 */
function NotificationFilters({
    keyword,
    onKeywordChange,
    readFilter,
    onReadFilterChange,
    typeFilter,
    onTypeFilterChange,
    hasUnread,
    isMarkingAll,
    onMarkAllAsRead,
    }: NotificationFiltersProps) {
    return (
        <div className="mb-5 space-y-3">
        {/* 검색창 */}
        <div className="relative">
            <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="pointer-events-none absolute left-3.5 top-1/2 h-4.5 w-4.5 -translate-y-1/2 text-text-muted"
            >
            <circle
                cx="11"
                cy="11"
                r="6.5"
                stroke="currentColor"
                strokeWidth="1.8"
            />
            <path
                d="m16 16 4 4"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
            </svg>

            <input
            type="search"
            value={keyword}
            onChange={(event) => onKeywordChange(event.target.value)}
            placeholder="알림 내용을 검색해 보세요"
            className="h-11 w-full rounded-[10px] border border-[#D8DEE8] bg-white pl-10 pr-4 text-sm text-ink outline-none transition-colors placeholder:text-text-muted focus:border-ink"
            />
        </div>

        {/* 필터와 전체 읽음 버튼 */}
        <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap gap-2">
            <select
                value={readFilter}
                onChange={(event) =>
                onReadFilterChange(
                    event.target.value as NotificationReadFilter,
                )
                }
                aria-label="읽음 상태 필터"
                className="h-9 rounded-[9px] border border-[#D8DEE8] bg-white px-3 text-sm text-ink outline-none"
            >
                <option value="ALL">전체 상태</option>
                <option value="UNREAD">읽지 않음</option>
                <option value="READ">읽음</option>
            </select>

            <select
                value={typeFilter}
                onChange={(event) =>
                onTypeFilterChange(
                    event.target.value as NotificationTypeFilter,
                )
                }
                aria-label="알림 종류 필터"
                className="h-9 rounded-[9px] border border-[#D8DEE8] bg-white px-3 text-sm text-ink outline-none"
            >
                <option value="ALL">전체 알림</option>
                <option value="GAME_START">경기 시작</option>
                <option value="SURGE">모멘텀 급상승</option>
            </select>
            </div>

            <button
            type="button"
            onClick={onMarkAllAsRead}
            disabled={!hasUnread || isMarkingAll}
            className="h-9 rounded-[9px] border border-[#D8DEE8] bg-white px-3.5 text-sm font-semibold text-ink transition-colors hover:bg-[#F4F6F9] disabled:cursor-not-allowed disabled:opacity-40"
            >
            {isMarkingAll ? '처리 중...' : '모두 읽음'}
            </button>
        </div>
        </div>
    );
}

export default NotificationFilters;