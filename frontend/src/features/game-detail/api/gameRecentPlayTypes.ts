import type {
    ApiInningType,
} from './gameDetailTypes';

/**
 * 해당 플레이가 끝난 시점의 홈·원정 점수다.
 */
export interface RecentPlayScoreResponse {
    home: number | null;
    away: number | null;
}

/**
 * 최근 플레이 API가 반환하는 개별 타석 결과다.
 *
 * 공개 모드에서만 사용하며 text는 화면에 표시할 완성 문구다.
 * 한국어 번역이 저장되어 있으면 translated가 true이고,
 * 번역이 아직 없으면 원문과 translated=false가 반환된다.
 */
export interface RecentPlayResponse {
    playId: number;
    inning: number;
    inningType: ApiInningType;
    text: string;
    translated: boolean;
    score: RecentPlayScoreResponse;
    observedAt: string;
}

/**
 * 최근 플레이 API의 최상위 응답이다.
 */
export interface RecentPlaysResponse {
    plays: RecentPlayResponse[];
}