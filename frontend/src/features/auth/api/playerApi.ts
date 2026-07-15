import apiClient from '../../../shared/api/httpClient';

/*
 * 관심 선수 설정 화면에서 사용하는 선수 검색 결과 타입입니다.
 *
 * 백엔드 GET /api/players?search= 응답의
 * PlayerSearchResponse 필드와 동일하게 맞춥니다.
 */
export interface PlayerSearchResponse {
    /*
     * balldontlie 선수 ID입니다.
     *
     * 관심 선수 저장 시 selectedPlayerIds 배열에
     * 이 값을 넣어야 합니다.
     */
    playerId: number;

    /*
     * 화면에 표시할 선수 전체 영문 이름입니다.
     */
    fullName: string;

    /*
     * 선수 포지션입니다.
     *
     * 선수 데이터 보강이 끝나지 않은 경우
     * null일 수 있습니다.
     */
    position: string | null;

    /*
     * 선수의 현재 소속팀 ID입니다.
     */
    teamId: number | null;

    /*
     * 선수의 현재 소속팀 전체 이름입니다.
     *
     * 소속팀 정보가 없거나 teams 테이블에서
     * 팀을 찾지 못하면 null일 수 있습니다.
     */
    teamName: string | null;

    /*
     * 선수의 현재 소속팀 약어입니다.
     *
     * 예:
     * LAD, NYY
     */
    teamAbbreviation: string | null;
}

/*
 * GET /api/players?search= 응답 전체 구조입니다.
 *
 * players:
 * - 검색된 선수 목록
 *
 * complete:
 * - true: 외부 balldontlie 검색이 정상적으로 완료됨
 * - false: 외부 검색 실패로 로컬 DB 결과만 반환됨
 */
export interface PlayerSearchResultResponse {
    players: PlayerSearchResponse[];
    complete: boolean;
}

/*
 * 선수 영문 이름 검색 API입니다.
 *
 * 호출 예:
 * searchPlayers('Ohtani')
 *
 * 실제 요청:
 * GET /api/players?search=Ohtani
 */
export const searchPlayers = async (
    search: string,
): Promise<PlayerSearchResultResponse> => {
    /*
     * 입력창 앞뒤 공백은 검색 의미가 없으므로 제거합니다.
     *
     * 비밀번호와 달리 선수 검색어이므로 trim 처리해도 됩니다.
     */
    const keyword = search.trim();

    /*
     * 빈 검색어로 서버에 요청하지 않습니다.
     *
     * 백엔드도 빈 검색어에 빈 배열을 반환하지만,
     * 프론트에서도 불필요한 네트워크 요청을 막습니다.
     */
    if (!keyword) {
        return {
        players: [],
        complete: true,
    };
    }

    const response =
        await apiClient.get<PlayerSearchResultResponse>(
            '/api/players',
            {
                params: {
                    search: keyword,
                },
            },
        );

    return response.data;
};