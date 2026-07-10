import apiClient from '../../../shared/api/httpClient';

/*
 * 마이페이지에서 사용하는 관심팀 응답 타입입니다.
 *
 * 백엔드 GET /api/members/me/preferences 응답의 favoriteTeams 배열과 맞춥니다.
 */
export interface FavoriteTeamResponse {
    /*
     * PULSE에서 관심팀 저장/수정에 사용하는 팀 ID입니다.
     *
     * PUT 요청의 selectedTeamIds에는 logoTeamId가 아니라 이 teamId를 넣어야 합니다.
     */
    teamId: number;

    /*
     * MLB 공식 로고 URL 생성에 사용하는 팀 ID입니다.
     *
     * 화면에는 logoUrl을 바로 사용하므로,
     * 이 값은 디버깅이나 추후 확장용으로 가지고 있습니다.
     */
    logoTeamId: number | null;

    /*
     * 팀 약어입니다.
     *
     * 예:
     * TB, TOR, LAD, NYY
     */
    abbreviation: string;

    /*
     * 화면에 표시할 팀 전체 이름입니다.
     */
    displayName: string;

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
     * 프론트 img src에 바로 넣을 수 있는 로고 URL입니다.
     *
     * 백엔드에서 logoTeamId 기반으로 조립해서 내려줍니다.
     */
    logoUrl: string | null;
}

/*
 * 알림 설정 타입입니다.
 *
 * all은 DB 컬럼이 아니라 프론트 UI용 전체 토글 값입니다.
 * 백엔드는 gameStart, surge, gameSwitch 기준으로 all 값을 계산해서 내려줍니다.
 */
export interface NotificationSettings {
    all: boolean;
    gameStart: boolean;
    surge: boolean;
    gameSwitch: boolean;
}

/*
 * 내 선호 설정 조회 응답 타입입니다.
 */
export interface UserPreferenceResponse {
    favoriteTeams: FavoriteTeamResponse[];
    notificationSettings: NotificationSettings;
}

/*
 * 내 선호 설정 수정 요청 타입입니다.
 *
 * selectedTeamIds:
 * - 사용자가 마이페이지에서 새로 선택한 관심팀 ID 목록
 * - 기존 관심팀은 백엔드에서 전체 삭제 후 이 목록으로 다시 저장합니다.
 */
export interface UserPreferenceUpdateRequest {
    selectedTeamIds: number[];
    notificationSettings: NotificationSettings;
}

/*
 * 로그인한 사용자의 관심팀 / 알림 설정 조회 API입니다.
 *
 * apiClient를 쓰는 이유:
 * - accessToken을 Authorization 헤더에 자동으로 붙여줍니다.
 * - accessToken이 만료되면 refreshToken 쿠키로 재발급 후 원래 요청을 재시도합니다.
 */
export const getMyPreferences =
    async (): Promise<UserPreferenceResponse> => {
        const response =
            await apiClient.get<UserPreferenceResponse>(
                '/api/members/me/preferences',
            );

        return response.data;
    };

/*
 * 로그인한 사용자의 관심팀 / 알림 설정 수정 API입니다.
 */
export const updateMyPreferences = async (
    request: UserPreferenceUpdateRequest,
): Promise<UserPreferenceResponse> => {
    const response =
        await apiClient.put<UserPreferenceResponse>(
            '/api/members/me/preferences',
            request,
        );

    return response.data;
};