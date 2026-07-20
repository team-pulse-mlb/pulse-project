import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import ApiUrl from '../../../shared/api/ApiUrl';
import {
    issueSseToken,
    type NotificationSummary,
} from '../../../shared/api/notificationApi';
import { showToast } from '../../../shared/components/toastEvent';
import { queryKeys } from '../../../shared/lib/queryKeys';

/**
 * 백엔드 notification_created SSE 이벤트의 data 구조입니다.
 */
interface NotificationCreatedPayload {
    notificationId: number;
}

/**
 * 재연결 최초 대기 시간입니다.
 */
const INITIAL_RECONNECT_DELAY_MS = 1_000;

/**
 * 연결 실패가 반복될 때 적용할 최대 대기 시간입니다.
 */
const MAX_RECONNECT_DELAY_MS = 30_000;

/**
 * 알 수 없는 JSON 값이 올바른 알림 생성 payload인지 확인합니다.
 */
function isNotificationCreatedPayload(
    value: unknown,
): value is NotificationCreatedPayload {
    if (
        typeof value !== 'object' ||
        value === null ||
        !('notificationId' in value)
    ) {
        return false;
    }

    const notificationId = Reflect.get(
        value,
        'notificationId',
    );

    return (
        typeof notificationId === 'number' &&
        Number.isInteger(notificationId) &&
        notificationId > 0
    );
}

/**
 * SSE 일회용 토큰을 포함한 연결 주소를 만듭니다.
 *
 * 로컬:
 * ApiUrl이 빈 문자열이므로 /api/sse?...가 되고,
 * Vite 프록시를 통해 localhost:8080으로 전달됩니다.
 *
 * 배포:
 * VITE_API_BASE_URL을 앞에 붙여 API 서버로 직접 연결합니다.
 */
function createSseUrl(token: string): string {
    const normalizedApiUrl = ApiUrl.replace(/\/+$/, '');

    return `${normalizedApiUrl}/api/sse?token=${encodeURIComponent(token)}`;
}

/**
 * 로그인 사용자의 알림 SSE 연결을 관리하는 Hook입니다.
 *
 * 처리 흐름:
 *
 * 1. POST /api/sse/token으로 일회용 토큰 발급
 * 2. GET /api/sse?token=...으로 EventSource 연결
 * 3. notification_created 수신
 * 4. 알림 목록 REST API 재조회
 * 5. 헤더 빨간 점과 드롭다운 목록 갱신
 * 6. 새 알림 메시지를 토스트로 표시
 *
 * 일회용 토큰은 한 번 사용하면 폐기되므로,
 * 연결이 끊어지면 기존 URL을 재사용하지 않고
 * 새 토큰을 발급해서 다시 연결합니다.
 */
export function useNotificationSse() {
    const queryClient = useQueryClient();

    /**
     * 동일한 이벤트가 중복 처리되는 것을 막기 위해
     * 마지막으로 처리한 알림 ID를 저장합니다.
     */
    const lastNotificationIdRef = useRef<number | null>(
        null,
    );

    useEffect(() => {
        /**
         * 컴포넌트가 언마운트됐는지 확인하는 값입니다.
         *
         * 로그아웃으로 NotificationBell이 사라진 뒤에는
         * 재연결이나 상태 갱신을 진행하지 않습니다.
         */
        let cancelled = false;

        /** 현재 열려 있는 SSE 연결 */
        let eventSource: EventSource | null = null;

        /** 재연결 예약 타이머 */
        let reconnectTimerId: number | null = null;

        /** 연속 연결 실패 횟수 */
        let reconnectAttempt = 0;

        /**
         * 재연결 대기 시간을 계산합니다.
         *
         * 1초 → 2초 → 4초 → 8초 순으로 증가하고
         * 최대 30초까지만 늘어납니다.
         */
        const getReconnectDelay = () => {
            const delay =
                INITIAL_RECONNECT_DELAY_MS *
                2 ** reconnectAttempt;

            return Math.min(
                delay,
                MAX_RECONNECT_DELAY_MS,
            );
        };

        /**
         * 새 토큰을 발급해 다시 연결하도록 예약합니다.
         */
        const scheduleReconnect = () => {
            if (
                cancelled ||
                reconnectTimerId !== null
            ) {
                return;
            }

            const delay = getReconnectDelay();

            reconnectAttempt += 1;

            reconnectTimerId = window.setTimeout(() => {
                reconnectTimerId = null;
                void connect();
            }, delay);
        };

        /**
         * 새 알림 SSE 이벤트를 처리합니다.
         */
        const handleNotificationCreated = async (
            event: Event,
        ) => {
            const messageEvent = event as MessageEvent<string>;

            let parsedData: unknown;

            try {
                parsedData = JSON.parse(messageEvent.data);
            } catch {
                return;
            }

            if (!isNotificationCreatedPayload(parsedData)) {
                return;
            }

            const { notificationId } = parsedData;

            /*
            * 동일한 알림 이벤트가 중복 전달되면
            * REST 재조회와 토스트를 반복하지 않습니다.
            */
            if (
                lastNotificationIdRef.current === notificationId
            ) {
                return;
            }

            lastNotificationIdRef.current = notificationId;

            /*
            * NotificationBell에서 알림 쿼리를 사용하고 있으므로
            * active 쿼리를 즉시 서버에서 다시 조회합니다.
            *
            * 이 작업으로:
            * - 종 아이콘의 빨간 점
            * - 드롭다운 최근 목록
            * - 전체 알림 페이지
            *
            * 가 같은 캐시를 기준으로 갱신됩니다.
            */
            await queryClient.refetchQueries({
                queryKey: queryKeys.me.notifications,
                type: 'active',
            });

            if (cancelled) {
                return;
            }

            const notifications =
                queryClient.getQueryData<
                    NotificationSummary[]
                >(queryKeys.me.notifications);

            const createdNotification =
                notifications?.find(
                    (notification) =>
                        notification.notificationId ===
                        notificationId,
                );

            /*
            * SSE payload에는 메시지가 없으므로,
            * REST 재조회로 얻은 서버의 실제 message를 토스트에 사용합니다.
            *
            * 프론트에서 임의의 경기 문구를 조립하지 않습니다.
            */
            if (createdNotification) {
                const isGameStart =
                    createdNotification.type === 'GAME_START';

                showToast({
                    title: isGameStart
                        ? '관심 팀 경기가 시작됐어요'
                        : '지금 볼 만한 경기 알림',
                    message: createdNotification.message,
                    to: `/games/${createdNotification.gameId}`,
                    variant: isGameStart
                        ? 'game-start'
                        : 'surge',
                    dedupeKey:
                        `notification-${createdNotification.notificationId}`,
                });
            }
        };

        /**
         * 일회용 토큰을 발급받아 SSE를 연결합니다.
         */
        const connect = async () => {
            if (cancelled) {
                return;
            }

            try {
                const token = await issueSseToken();

                if (cancelled) {
                    return;
                }

                eventSource = new EventSource(
                    createSseUrl(token),
                    {
                        withCredentials: true,
                    },
                );

                /**
                 * SSE 연결에 성공하면:
                 *
                 * 1. 연속 연결 실패 횟수를 초기화합니다.
                 * 2. 연결이 끊겨 있던 동안 생성된 알림을 복구하기 위해
                 *    알림 목록을 한 번 다시 조회합니다.
                 *
                 * SSE는 연결이 끊긴 동안 발생한 과거 이벤트를
                 * 자동으로 다시 전달하지 않으므로 REST 재조회가 필요합니다.
                 */
                eventSource.onopen = () => {
                    reconnectAttempt = 0;

                    void queryClient.refetchQueries({
                        queryKey: queryKeys.me.notifications,
                        type: 'active',
                    });
                };

                eventSource.addEventListener(
                    'notification_created',
                    handleNotificationCreated,
                );

                /**
                 * 연결 오류 발생 시 기존 EventSource를 닫습니다.
                 *
                 * 브라우저의 기본 자동 재연결은 동일한 일회용 토큰 URL을
                 * 재사용하므로 백엔드에서 401이 발생할 수 있습니다.
                 *
                 * 따라서 직접 닫고 새 토큰으로 다시 연결합니다.
                 */
                eventSource.onerror = () => {
                    eventSource?.close();
                    eventSource = null;

                    scheduleReconnect();
                };
            } catch {
                /*
                * 토큰 발급 또는 연결 준비에 실패한 경우에도
                * 일정 시간 뒤 새 토큰 발급부터 다시 시도합니다.
                */
                scheduleReconnect();
            }
        };

        void connect();

        /**
         * 로그아웃 또는 Header 언마운트 시:
         * - 재연결 예약 취소
         * - SSE 연결 종료
         */
        return () => {
            cancelled = true;

            if (reconnectTimerId !== null) {
                window.clearTimeout(reconnectTimerId);
            }

            eventSource?.close();
        };
    }, [queryClient]);
}