import {
    useQuery,
} from '@tanstack/react-query';

import type {
    DisplayMode,
} from '../lib/displayMode';
import {
    getGameRecentPlays,
} from './gameRecentPlayApi';

/**
 * 최근 플레이 조회에 사용하는 React Query 키다.
 *
 * 경기와 공개 상태가 달라지면 서로 다른 캐시를 사용한다.
 */
export const gameRecentPlaysQueryKeys = {
    detail: (
        gameId: number,
        mode: DisplayMode,
    ) => [
        'game-recent-plays',
        gameId,
        mode,
    ] as const,
};

/**
 * 경기 상세 화면의 최근 플레이를 조회한다.
 *
 * 최근 플레이에는 점수와 플레이 원문이 포함되므로
 * GameDetailPage가 공개 모드일 때만 enabled를 true로 전달한다.
 */
export function useGameRecentPlaysQuery(
    gameId: number | null,
    mode: DisplayMode,
    enabled: boolean,
) {
    return useQuery({
        queryKey:
            gameId === null
                ? [
                    'game-recent-plays',
                    'disabled',
                    mode,
                ] as const
                : gameRecentPlaysQueryKeys.detail(
                    gameId,
                    mode,
                ),
        queryFn: () => {
            if (gameId === null) {
                throw new Error(
                    '최근 플레이를 조회할 경기 ID가 없습니다.',
                );
            }

            return getGameRecentPlays(
                gameId,
                mode,
            );
        },
        /*
         * 유효한 경기 ID가 있고 공개 모드인 경우에만
         * 최근 플레이 API 요청을 실행한다.
         */
        enabled:
            enabled
            && gameId !== null
            && mode === 'REVEALED',
    });
}