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
 * 최근 플레이에는 점수, 이닝 초·말, 실제 플레이 문구가 포함되므로
 * 공개 모드 화면에서만 사용한다.
 */
export interface RecentPlayResponse {
    playId: number;
    inning: number;
    inningType: ApiInningType;
    text: string;
    score: RecentPlayScoreResponse;
    observedAt: string;
}

/**
 * 최근 플레이 API의 최상위 응답이다.
 */
export interface RecentPlaysResponse {
    plays: RecentPlayResponse[];
}