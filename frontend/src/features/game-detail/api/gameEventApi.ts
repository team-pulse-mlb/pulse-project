import apiClient from '../../../shared/api/httpClient';
import type { DisplayMode } from '../lib/displayMode';
import type { GameEventsResponse } from './gameEventTypes';

/**
 * 경기 상세 이벤트 타임라인을 조회한다.
 *
 * 보호·공개 모드는 반환되는 필드와 이벤트 범위가 다르므로
 * 사용자가 현재 선택한 mode를 항상 명시적으로 전달한다.
 */
export async function getGameEvents(
    gameId: number,
    mode: DisplayMode,
): Promise<GameEventsResponse> {
    const response =
        await apiClient.get<GameEventsResponse>(
            `/api/games/${gameId}/events`,
            {
                params: {
                    mode,
                },
            },
        );

    return response.data;
}