import { API_BASE_URL, parseJsonResponse } from '../../../shared/api/httpClient'
import type { GameDetailRequestMode, GameDetailResponse } from '../types'

/**
 * 경기 상세 API를 호출한다.
 *
 * 백엔드 엔드포인트:
 * GET /api/games/{gameId}?mode=protected
 * GET /api/games/{gameId}?mode=revealed
 *
 * protected:
 * - 기본 화면에서 사용한다.
 * - 점수, 팀명, play 원문 같은 스포일러 정보를 받지 않는다.
 *
 * revealed:
 * - 사용자가 공개 전환을 선택한 뒤 사용한다.
 * - 점수, 팀명, play 원문을 포함한 응답을 받는다.
 */
export async function fetchGameDetail(
    gameId: string,
    mode: GameDetailRequestMode,
): Promise<GameDetailResponse> {
    const url = `${API_BASE_URL}/api/games/${gameId}?mode=${mode}`

    const response = await fetch(url)

    return parseJsonResponse<GameDetailResponse>(response)
}