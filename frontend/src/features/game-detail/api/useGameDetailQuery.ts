import { useQuery } from '@tanstack/react-query';

import type { DisplayMode } from '../lib/displayMode';
import { getGameDetail } from './gameDetailApi';

/**
 * 경기 상세 조회에서 사용하는 React Query 키다.
 *
 * 보호·공개 응답은 포함되는 필드가 다르므로
 * mode를 반드시 queryKey에 포함한다.
 */
export const gameDetailQueryKeys = {
    all: ['game-detail'] as const,

    detail: (
        gameId: number,
        mode: DisplayMode,
    ) =>
        [
            ...gameDetailQueryKeys.all,
            gameId,
            mode,
        ] as const,
};

/**
 * 경기 상세 API를 조회한다.
 *
 * gameId가 유효하지 않으면 요청을 실행하지 않는다.
 * 현재 단계에서는 polling이나 SSE를 붙이지 않고
 * 최초 조회와 모드 전환 재조회만 담당한다.
 */
export function useGameDetailQuery(
    gameId: number | null,
    mode: DisplayMode,
) {
    return useQuery({
        queryKey:
            gameId === null
                ? [
                    ...gameDetailQueryKeys.all,
                    'invalid',
                    mode,
                ] as const
                : gameDetailQueryKeys.detail(
                    gameId,
                    mode,
                ),

        queryFn: () => {
            if (gameId === null) {
                throw new Error(
                    '유효한 경기 ID가 필요합니다.',
                );
            }

            return getGameDetail(
                gameId,
                mode,
            );
        },

        enabled: gameId !== null,

        /**
         * 일시적인 네트워크 실패에는 한 번만 재시도한다.
         * 잘못된 경기 ID를 반복해서 호출하지 않도록
         * 과도한 기본 재시도를 피한다.
         */
        retry: 1,
    });
}