import {
    useQuery,
} from '@tanstack/react-query';

import type {
    DisplayMode,
} from '../lib/displayMode';
import {
    getGameEvents,
} from './gameEventApi';

/**
 * 경기 이벤트 조회에 사용하는 React Query 키다.
 *
 * 경기와 보호·공개 모드가 달라지면
 * 서로 다른 캐시를 사용한다.
 */
export const gameEventsQueryKeys = {
    detail: (
        gameId: number,
        mode: DisplayMode,
    ) => [
        'game-events',
        gameId,
        mode,
    ] as const,
};

/**
 * 경기 상세 화면의 이벤트 타임라인을 조회한다.
 *
 * 예정 경기에서는 enabled를 false로 전달해서
 * 이벤트 API 요청을 실행하지 않는다.
 */
export function useGameEventsQuery(
    gameId: number | null,
    mode: DisplayMode,
    enabled: boolean,
) {
    return useQuery({
        queryKey:
            gameId === null
                ? [
                    'game-events',
                    'disabled',
                    mode,
                ] as const
                : gameEventsQueryKeys.detail(
                    gameId,
                    mode,
                ),

        queryFn: () => {
            if (gameId === null) {
                throw new Error(
                    '이벤트를 조회할 경기 ID가 없습니다.',
                );
            }

            return getGameEvents(
                gameId,
                mode,
            );
        },

        /**
         * 유효한 경기 ID가 있고
         * 진행 또는 종료 경기일 때만 요청한다.
         */
        enabled:
            enabled
            && gameId !== null,
    });
}