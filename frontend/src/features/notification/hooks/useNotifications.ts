import {
    useMutation,
    useQuery,
    useQueryClient,
} from '@tanstack/react-query';

import {
    fetchMyNotifications,
    markAllNotificationsAsRead,
    markSelectedNotificationsAsRead,
    type NotificationSummary,
} from '../../../shared/api/notificationApi';
import { queryKeys } from '../../../shared/lib/queryKeys';

/**
 * 알림 조회와 읽음 처리를 담당하는 공통 Hook입니다.
 *
 * 페이지와 헤더 드롭다운에서 같은 Hook을 사용하면
 * 동일한 React Query 캐시를 공유할 수 있습니다.
 *
 * 결과적으로:
 * - 알림 페이지에서 읽음 처리하면 헤더의 빨간 점도 사라지고
 * - SSE로 새 알림이 들어오면 페이지와 헤더가 함께 갱신됩니다.
 */
export function useNotifications() {
    const queryClient = useQueryClient();

    /**
     * 현재 로그인 사용자의 최근 7일 알림 조회
     */
    const notificationsQuery = useQuery({
        queryKey: queryKeys.me.notifications,
        queryFn: fetchMyNotifications,
        retry: false,
    });

    /**
     * 선택한 알림 읽음 처리
     */
    const selectedReadMutation = useMutation({
        mutationFn: markSelectedNotificationsAsRead,

        /*
        * API 요청이 성공하면 서버 재조회 전에
        * React Query 캐시를 먼저 수정해 화면에 즉시 반영합니다.
        */
        onSuccess: (_response, notificationIds) => {
        const readAt = new Date().toISOString();

        queryClient.setQueryData<NotificationSummary[]>(
            queryKeys.me.notifications,
            (currentNotifications) => {
            if (!currentNotifications) {
                return currentNotifications;
            }

            return currentNotifications.map((notification) => {
                const shouldMarkAsRead = notificationIds.includes(
                notification.notificationId,
                );

                if (!shouldMarkAsRead) {
                return notification;
                }

                return {
                ...notification,
                read: true,
                readAt,
                };
            });
            },
        );

        /*
        * 프론트에서 임시로 넣은 readAt 대신
        * 서버의 정확한 값을 다시 가져와 동기화합니다.
        */
        void queryClient.invalidateQueries({
            queryKey: queryKeys.me.notifications,
        });
        },
    });

    /**
     * 모든 미읽음 알림 읽음 처리
     */
    const allReadMutation = useMutation({
        mutationFn: markAllNotificationsAsRead,

        onSuccess: () => {
        const readAt = new Date().toISOString();

        queryClient.setQueryData<NotificationSummary[]>(
            queryKeys.me.notifications,
            (currentNotifications) => {
            if (!currentNotifications) {
                return currentNotifications;
            }

            return currentNotifications.map((notification) => ({
                ...notification,
                read: true,
                readAt: notification.readAt ?? readAt,
            }));
            },
        );

        void queryClient.invalidateQueries({
            queryKey: queryKeys.me.notifications,
        });
        },
    });

    const notifications = notificationsQuery.data ?? [];

    /**
     * 한 건이라도 읽지 않은 알림이 존재하는지 확인합니다.
     *
     * Header의 빨간 점과 전체 읽음 버튼 활성화에 사용합니다.
     */
    const hasUnread = notifications.some(
        (notification) => !notification.read,
    );

    /**
     * 알림 한 건을 읽음 처리합니다.
     */
    const markOneAsRead = async (notificationId: number) => {
        return selectedReadMutation.mutateAsync([notificationId]);
    };

    /**
     * 여러 알림을 선택해서 읽음 처리할 때 사용할 수 있습니다.
     */
    const markSelectedAsRead = async (notificationIds: number[]) => {
        return selectedReadMutation.mutateAsync(notificationIds);
    };

    /**
     * 모든 미읽음 알림을 읽음 처리합니다.
     */
    const markAllAsRead = async () => {
        return allReadMutation.mutateAsync();
    };

    return {
        notifications,
        hasUnread,

        isLoading: notificationsQuery.isPending,
        isError: notificationsQuery.isError,
        isSuccess: notificationsQuery.isSuccess,

        isMarkingSelected: selectedReadMutation.isPending,
        isMarkingAll: allReadMutation.isPending,

        refetch: notificationsQuery.refetch,

        markOneAsRead,
        markSelectedAsRead,
        markAllAsRead,
  };
}