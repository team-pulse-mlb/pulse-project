import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import EmptyState from '../../../shared/components/EmptyState';
import NotificationFilters, {
  type NotificationReadFilter,
  type NotificationTypeFilter,
} from '../components/NotificationFilters';
import NotificationList from '../components/NotificationList';
import { useNotifications } from '../hooks/useNotifications';
import { createNotificationSearchText } from '../utils/notificationFormat';

/**
 * 전체 알림 센터 페이지입니다.
 *
 * 담당 역할:
 * - 알림 Hook과 화면 컴포넌트 조립
 * - 검색어 및 필터 상태 관리
 * - 알림 클릭 시 읽음 처리 후 경기 상세 이동
 *
 * API 통신과 React Query 캐시 처리는
 * useNotifications Hook이 담당합니다.
 */

// 알림 센터 골격 (USER_FLOW §4.10).
// 최신순 목록, 미읽음 ●/읽음 ○, 클릭 시 읽음 처리 + 상세 이동.
// 구현은 local-docs/CODEX_FRONTEND_PROMPT.md 위임 명세를 따른다. 데이터 연결은 윤호 담당.
function NotificationsPage() {
  const navigate = useNavigate();

  const {
    notifications,
    hasUnread,
    isLoading,
    isError,
    isSuccess,
    isMarkingSelected,
    isMarkingAll,
    refetch,
    markOneAsRead,
    markAllAsRead,
  } = useNotifications();

  /** 알림 메시지 검색어 */
  const [keyword, setKeyword] = useState('');

  /** 전체·미읽음·읽음 필터 */
  const [readFilter, setReadFilter] =
    useState<NotificationReadFilter>('ALL');

  /** 전체·경기 시작·모멘텀 급상승 필터 */
  const [typeFilter, setTypeFilter] =
    useState<NotificationTypeFilter>('ALL');

  /** 읽음 처리 실패 시 표시할 오류 */
  const [actionError, setActionError] = useState<string | null>(null);

  /**
   * 원본 알림 목록에 검색어와 필터를 적용합니다.
   *
   * 현재 백엔드는 최근 7일 알림만 반환하므로
   * 우선 프론트에서 필터링해도 데이터 크기 부담이 크지 않습니다.
   */
  const filteredNotifications = useMemo(() => {
    const normalizedKeyword = keyword.trim().toLowerCase();

    return notifications.filter((notification) => {
      /**
       * 검색 조건
       *
       * 검색어가 없으면 모든 알림이 검색 조건을 통과합니다.
       */
      const matchesKeyword =
        normalizedKeyword.length === 0 ||
        createNotificationSearchText(
          notification.type,
          notification.message,
        ).includes(normalizedKeyword);

      /**
       * 읽음 상태 조건
       */
      const matchesReadFilter =
        readFilter === 'ALL' ||
        (readFilter === 'UNREAD' && !notification.read) ||
        (readFilter === 'READ' && notification.read);

      /**
       * 알림 종류 조건
       */
      const matchesTypeFilter =
        typeFilter === 'ALL' ||
        notification.type === typeFilter;

      return (
        matchesKeyword &&
        matchesReadFilter &&
        matchesTypeFilter
      );
    });
  }, [keyword, notifications, readFilter, typeFilter]);

  /**
   * 알림 한 건을 클릭했을 때 실행합니다.
   *
   * 미읽음 상태:
   * 1. 읽음 처리 API 호출
   * 2. 성공하면 경기 상세 페이지 이동
   *
   * 이미 읽은 상태:
   * API 호출 없이 바로 경기 상세 페이지로 이동
   */
  const handleNotificationClick = async (
    notification: (typeof notifications)[number],
  ) => {
    setActionError(null);

    if (!notification.read) {
      try {
        await markOneAsRead(notification.notificationId);
      } catch {
        setActionError(
          '알림을 읽음 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        );
        return;
      }
    }

    navigate(`/games/${notification.gameId}`);
  };

  /**
   * 현재 사용자의 모든 미읽음 알림을 읽음 처리합니다.
   */
  const handleMarkAllAsRead = async () => {
    setActionError(null);

    try {
      await markAllAsRead();
    } catch {
      setActionError(
        '모든 알림을 읽음 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.',
      );
    }
  };

  return (
    <div className="mx-auto max-w-[680px] px-4 py-8">
      {/* 페이지 제목 */}
      <div className="mb-5">
        <h1 className="text-2xl font-bold text-ink">
          알림 센터
        </h1>

        <p className="mt-1 text-sm text-text-muted">
          최근 7일 동안 받은 경기 알림을 확인할 수 있습니다.
        </p>
      </div>

      {/* 검색·필터·모두 읽음 */}
      <NotificationFilters
        keyword={keyword}
        onKeywordChange={setKeyword}
        readFilter={readFilter}
        onReadFilterChange={setReadFilter}
        typeFilter={typeFilter}
        onTypeFilterChange={setTypeFilter}
        hasUnread={hasUnread}
        isMarkingAll={isMarkingAll}
        onMarkAllAsRead={() => {
          void handleMarkAllAsRead();
        }}
      />

      {/* 읽음 처리 오류 */}
      {actionError && (
        <div
          role="alert"
          className="mb-4 rounded-[10px] border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
        >
          {actionError}
        </div>
      )}

      {/* 목록 로딩 */}
      {isLoading && (
        <div className="rounded-panel border border-[#E3E7ED] bg-white px-6 py-12 text-center text-sm text-text-muted">
          알림을 불러오는 중입니다.
        </div>
      )}

      {/* 조회 오류 */}
      {isError && (
        <div className="rounded-panel border border-red-200 bg-red-50 px-6 py-8 text-center">
          <p className="text-sm text-red-700">
            알림 목록을 불러오지 못했습니다.
          </p>

          <button
            type="button"
            onClick={() => {
              void refetch();
            }}
            className="mt-4 rounded-[9px] bg-ink px-4 py-2 text-sm font-semibold text-white"
          >
            다시 시도
          </button>
        </div>
      )}

      {isSuccess && notifications.length === 0 && (
        <EmptyState message="최근 7일 동안 받은 알림이 없습니다." />
      )}

      {isSuccess &&
        notifications.length > 0 &&
        filteredNotifications.length === 0 && (
          <EmptyState message="검색 조건에 맞는 알림이 없습니다." />
        )}

      {isSuccess && filteredNotifications.length > 0 && (
        <NotificationList
          notifications={filteredNotifications}
          onNotificationClick={(notification) => {
            void handleNotificationClick(notification);
          }}
          disabled={isMarkingSelected}
        />
      )}
    </div>
  );
}

export default NotificationsPage;