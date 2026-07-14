import apiClient from '../../../shared/api/httpClient';
import type { DisplayMode } from '../lib/displayMode';
import type { GameDetailResponse } from './gameDetailTypes';

/**
 * 경기 상세 정보를 조회한다.
 *
 * 백엔드는 mode가 없거나 잘못된 경우 보호 모드로 처리하지만,
 * 프론트에서는 현재 사용자가 선택한 모드를 명시적으로 전달한다.
 */
export async function getGameDetail(
    gameId: number,
    mode: DisplayMode,
): Promise<GameDetailResponse> {
    const response =
        await apiClient.get<GameDetailResponse>(
            `/api/games/${gameId}`,
            {
                params: {
                    mode,
                },
            },
        );

    return response.data;
}