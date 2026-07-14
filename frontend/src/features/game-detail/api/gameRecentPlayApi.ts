import apiClient from '../../../shared/api/httpClient';
import type {
    DisplayMode,
} from '../lib/displayMode';
import type {
    RecentPlaysResponse,
} from './gameRecentPlayTypes';

/**
 * 경기 상세 화면의 최근 타석 결과를 조회한다.
 *
 * 최근 플레이에는 점수와 실제 플레이 문구가 포함되므로
 * GameDetailPage에서는 공개 모드일 때만 이 함수를 호출한다.
 */
export async function getGameRecentPlays(
    gameId: number,
    mode: DisplayMode,
): Promise<RecentPlaysResponse> {
    const response =
        await apiClient.get<RecentPlaysResponse>(
            `/api/games/${gameId}/recent-plays`,
            {
                params: {
                    mode,
                },
            },
        );

    return response.data;
}