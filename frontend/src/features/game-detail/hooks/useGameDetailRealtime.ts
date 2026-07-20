import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import ApiUrl from '../../../shared/api/ApiUrl';
import { gameDetailQueryKeys } from '../api/useGameDetailQuery';
import { gameEventsQueryKeys } from '../api/useGameEventsQuery';
import { gameRecentPlaysQueryKeys } from '../api/useGameRecentPlaysQuery';
import type { DisplayMode } from '../lib/displayMode';

const SSE_COALESCE_MS = 3_000;
const FALLBACK_REFRESH_MS = 10_000;

interface UseGameDetailRealtimeParams {
    gameId: number | null;
    mode: DisplayMode;
    enabled: boolean;
}

/**
 * LIVE 경기 상세에만 사용하는 실시간 갱신 훅이다.
 *
 * 공용 홈 SSE 로직은 변경하지 않고,
 * 현재 상세 화면에서 실제로 사용하는 쿼리 키만 갱신한다.
 *
 * SSE가 누락되거나 늦게 도착하는 상황에 대비해
 * 화면이 보이는 동안 10초 간격의 보조 갱신도 수행한다.
 */
export function useGameDetailRealtime({
                                          gameId,
                                          mode,
                                          enabled,
                                      }: UseGameDetailRealtimeParams) {
    const queryClient =
        useQueryClient();

    useEffect(() => {
        if (
            !enabled
            || gameId === null
        ) {
            return;
        }

        let invalidateTimer:
            ReturnType<typeof setTimeout>
            | undefined;

        const invalidateCurrentGame = () => {
            void Promise.all([
                queryClient.invalidateQueries({
                    queryKey:
                        gameDetailQueryKeys.detail(
                            gameId,
                            mode,
                        ),
                }),

                queryClient.invalidateQueries({
                    queryKey:
                        gameEventsQueryKeys.detail(
                            gameId,
                            mode,
                        ),
                }),

                queryClient.invalidateQueries({
                    queryKey:
                        gameRecentPlaysQueryKeys.detail(
                            gameId,
                            mode,
                        ),
                }),
            ]);
        };

        /*
         * 짧은 시간에 같은 경기 신호가 연속으로 도착해도
         * 상세 API 요청은 한 번만 실행한다.
         */
        const scheduleInvalidation = () => {
            if (invalidateTimer !== undefined) {
                return;
            }

            invalidateTimer = setTimeout(() => {
                invalidateTimer = undefined;
                invalidateCurrentGame();
            }, SSE_COALESCE_MS);
        };

        const source =
            new EventSource(
                `${ApiUrl}/api/sse`,
            );

        source.addEventListener(
            'game_updated',
            (event) => {
                try {
                    const payload =
                        JSON.parse(
                            (event as MessageEvent)
                                .data,
                        ) as {
                            gameId: number;
                        };

                    if (payload.gameId !== gameId) {
                        return;
                    }

                    scheduleInvalidation();
                } catch {
                    /*
                     * 잘못된 SSE 신호는 무시한다.
                     * EventSource 연결은 유지되어 다음 정상 신호를 받는다.
                     */
                }
            },
        );

        /*
         * SSE가 일시적으로 누락돼도 LIVE 화면이 멈추지 않도록
         * 브라우저 탭이 보일 때만 보조 갱신한다.
         */
        const fallbackInterval =
            setInterval(() => {
                if (
                    document.visibilityState
                    === 'visible'
                ) {
                    invalidateCurrentGame();
                }
            }, FALLBACK_REFRESH_MS);

        return () => {
            source.close();

            clearInterval(
                fallbackInterval,
            );

            if (invalidateTimer !== undefined) {
                clearTimeout(
                    invalidateTimer,
                );
            }
        };
    }, [
        enabled,
        gameId,
        mode,
        queryClient,
    ]);
}
