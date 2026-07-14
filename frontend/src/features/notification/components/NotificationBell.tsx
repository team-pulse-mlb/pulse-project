import {
    useEffect,
    useMemo,
    useRef,
    useState,
} from 'react';
import { useLocation, useNavigate } from 'react-router';

import type { NotificationSummary } from '../../../shared/api/notificationApi';
import { useNotifications } from '../hooks/useNotifications';
import NotificationDropdown from './NotificationDropdown';
import { useNotificationSse } from '../hooks/useNotificationSse';

/**
 * 헤더에 표시할 종 아이콘입니다.
 */
function BellIcon() {
    return (
        <svg
        viewBox="0 0 24 24"
        className="h-5 w-5"
        fill="none"
        aria-hidden="true"
        >
        <path
            d="M6 9a6 6 0 1 1 12 0c0 4 1.5 5.5 1.5 5.5h-15S6 13 6 9Z"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
        />

        <path
            d="M10 18.5a2.2 2.2 0 0 0 4 0"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
        />
        </svg>
    );
}

/**
 * 헤더의 알림 종 아이콘과 드롭다운을 관리합니다.
 *
 * 담당 기능:
 * - 종 아이콘 클릭으로 드롭다운 열기·닫기
 * - 미읽음 알림 빨간 점 표시
 * - 최근 알림 5개 표시
 * - 바깥 영역 클릭 시 닫기
 * - ESC 키로 닫기
 * - 알림 클릭 시 읽음 처리 후 경기 상세 이동
 */
function NotificationBell() {
    /*
     * NotificationBell은 로그인 상태에서만 Header에 렌더링됩니다.
     * 따라서 이 컴포넌트가 존재하는 동안에만 인증 SSE를 연결합니다.
     */
    useNotificationSse();

    const navigate = useNavigate();
    const location = useLocation();

    /**
     * 드롭다운 영역 전체를 가리키는 DOM 참조입니다.
     * 바깥 영역 클릭 여부를 판단하는 데 사용합니다.
     */
    const containerRef = useRef<HTMLDivElement>(null);

    /** 드롭다운 열림 상태 */
    const [isOpen, setIsOpen] = useState(false);

    /** 읽음 처리 오류 문구 */
    const [actionError, setActionError] =
        useState<string | null>(null);

    const {
        notifications,
        hasUnread,
        isLoading,
        isError,
        isMarkingSelected,
        isMarkingAll,
        refetch,
        markOneAsRead,
        markAllAsRead,
    } = useNotifications();

    /**
     * 드롭다운에서는 전체 알림을 모두 보여주지 않고
     * 최신순 최대 5개만 표시합니다.
     *
     * 백엔드 응답 자체가 최신순이므로 앞에서 5개를 가져옵니다.
     */
    const recentNotifications = useMemo(
        () => notifications.slice(0, 5),
        [notifications],
    );

    /**
     * 현재 전체 알림 페이지에 있는 경우
     * 종 아이콘에 활성 스타일을 적용합니다.
     */
    const isActive =
        location.pathname.startsWith('/notifications');

    /**
     * 드롭다운이 열린 동안 바깥 영역을 클릭하면 닫습니다.
     */
    useEffect(() => {
        if (!isOpen) {
        return;
        }

        const handleOutsideClick = (event: MouseEvent) => {
        const target = event.target;

        if (!(target instanceof Node)) {
            return;
        }

        if (
            containerRef.current &&
            !containerRef.current.contains(target)
        ) {
            setIsOpen(false);
        }
        };

        const handleEscapeKey = (event: KeyboardEvent) => {
        if (event.key === 'Escape') {
            setIsOpen(false);
        }
        };

        document.addEventListener(
        'mousedown',
        handleOutsideClick,
        );

        document.addEventListener(
        'keydown',
        handleEscapeKey,
        );

        return () => {
        document.removeEventListener(
            'mousedown',
            handleOutsideClick,
        );

        document.removeEventListener(
            'keydown',
            handleEscapeKey,
        );
        };
    }, [isOpen]);

    /**
     * 다른 페이지로 이동하면 열려 있던 드롭다운을 닫습니다.
     */
    useEffect(() => {
        setIsOpen(false);
    }, [location.pathname]);

    /**
     * 알림 한 건 클릭 처리
     *
     * 1. 미읽음이면 읽음 API 호출
     * 2. 드롭다운 닫기
     * 3. 해당 경기 상세 페이지 이동
     */
    const handleNotificationClick = async (
        notification: NotificationSummary,
    ) => {
        setActionError(null);

        if (!notification.read) {
        try {
            await markOneAsRead(
            notification.notificationId,
            );
        } catch {
            setActionError(
            '알림을 읽음 처리하지 못했습니다.',
            );
            return;
        }
        }

        setIsOpen(false);

        navigate(`/games/${notification.gameId}`);
    };

    /**
     * 모든 미읽음 알림을 읽음 처리합니다.
     */
    const handleMarkAllAsRead = async () => {
        setActionError(null);

        try {
        await markAllAsRead();
        } catch {
        setActionError(
            '모든 알림을 읽음 처리하지 못했습니다.',
        );
        }
    };

    return (
        <div
        ref={containerRef}
        className="relative"
        >
        <button
            type="button"
            aria-label="알림 열기"
            aria-haspopup="dialog"
            aria-expanded={isOpen}
            onClick={() => {
            setActionError(null);
            setIsOpen((current) => !current);
            }}
            className={`relative flex h-9 w-9 items-center justify-center rounded-[9px] text-white/85 transition-colors hover:bg-white/10 hover:text-white ${
            isOpen || isActive
                ? 'bg-white/10 text-white'
                : ''
            }`}
        >
            <BellIcon />

            {/* 미읽음 알림이 존재할 때만 빨간 점 표시 */}
            {hasUnread && (
            <span
                aria-label="읽지 않은 알림이 있습니다."
                className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-mlb-red"
            />
            )}
        </button>

        {isOpen && (
            <NotificationDropdown
            notifications={recentNotifications}
            hasUnread={hasUnread}
            isLoading={isLoading}
            isError={isError}
            isMarkingSelected={isMarkingSelected}
            isMarkingAll={isMarkingAll}
            actionError={actionError}
            onNotificationClick={(notification) => {
                void handleNotificationClick(notification);
            }}
            onMarkAllAsRead={() => {
                void handleMarkAllAsRead();
            }}
            onRetry={() => {
                void refetch();
            }}
            onClose={() => setIsOpen(false)}
            />
        )}
        </div>
    );
}

export default NotificationBell;