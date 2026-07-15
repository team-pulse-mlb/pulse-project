import axios from 'axios';

import ApiUrl from './ApiUrl';

/*
 * 백엔드 GET /api/teams 응답 타입입니다.
 *
 * 사용 위치:
 * - 회원가입 Step 2 관심팀 선택
 * - 추후 마이페이지 관심팀 변경
 */
export interface TeamResponse {
    /*
     * PULSE에서 관심팀 저장에 사용할 팀 ID입니다.
     *
     * 회원가입 요청의 selectedTeamIds에는 이 값이 들어갑니다.
     */
    teamId: number;

    /*
     * MLB 공식 로고 URL 생성에 사용되는 팀 ID입니다.
     */
    logoTeamId: number | null;

    /*
     * 팀 약어입니다.
     *
     * 예:
     * LAD, NYY, BOS
     */
    abbreviation: string;

    /*
     * 화면에 표시할 전체 팀명입니다.
     *
     * 예:
     * Los Angeles Dodgers
     */
    displayName: string;

    /*
     * 짧은 팀명입니다.
     *
     * 예:
     * Dodgers
     */
    shortDisplayName: string | null;

    /*
     * 리그 정보입니다.
     *
     * 예:
     * American, National
     */
    league: string;

    /*
     * 지구 정보입니다.
     *
     * 예:
     * East, Central, West
     */
    division: string;

    /*
     * 프론트에서 img src로 바로 사용할 로고 URL입니다.
     */
    logoUrl: string | null;
}

/*
 * 전체 MLB 팀 목록 조회 API.
 *
 * 로그인 전 회원가입 화면에서 호출하므로 accessToken이 필요 없습니다.
 */
export const getTeams = async (): Promise<TeamResponse[]> => {
    const response = await axios.get<TeamResponse[]>(
        `${ApiUrl}/api/teams`,
    );

    // 외부 API의 음수 ID는 실제 선택 가능한 MLB 팀이 아닌 임시 팀이다.
    return response.data.filter((team) => team.teamId >= 0);
};
