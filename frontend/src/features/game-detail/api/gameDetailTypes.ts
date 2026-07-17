import type { DisplayMode } from '../lib/displayMode';

export type GameStatus =
    | 'STATUS_SCHEDULED'
    | 'STATUS_IN_PROGRESS'
    | 'STATUS_FINAL';

/**
 * 백엔드 plays.inning_type에서 전달되는 값이다.
 *
 * 화면 컴포넌트는 TOP/BOTTOM을 사용하므로
 * 이후 변환 계층에서 대문자로 변환한다.
 */
export type ApiInningType =
    | 'Top'
    | 'Bottom';

export interface TeamResponse {
    id: number | null;
    name: string | null;
    abbr: string | null;

    /*
     * 백엔드가 teams.logo_team_id를 이용해 만든
     * MLB 공식 팀 로고 URL이다.
     */
    logoUrl: string | null;
}

export interface ScoreResponse {
    home: number | null;
    away: number | null;
}

export interface SituationResponse {
    outs: number | null;
    balls: number | null;
    strikes: number | null;

    runnerOnFirst: boolean;
    runnerOnSecond: boolean;
    runnerOnThird: boolean;

    scoringPosition: boolean;
    basesLoaded: boolean;
}

export interface PlayerResponse {
    id: number;
    name: string;
}

export interface CurrentMatchupResponse {
    batter: PlayerResponse;
    pitcher: PlayerResponse;
}

export interface InningScoresResponse {
    /**
     * 마지막 홈 공격이 생략된 경우처럼
     * 이닝별 값이 null일 수 있다.
     */
    away: Array<number | null>;
    home: Array<number | null>;
}

export interface ProbablePitchersResponse {
    home: string | null;
    away: string | null;
}

export interface MatchupResponse {
    home: string | null;
    away: string | null;
}

export interface SwitchSuggestionResponse {
    gameId: number;
    matchup: MatchupResponse;
    latestTag: string | null;
}

export interface ScoringPlayResponse {
    inning: number | null;
    inningType: ApiInningType | null;
    text: string;
}

export interface ProtectedTensionPointResponse {
    inning: number | null;
    level: number | null;
}

export interface RevealedTensionPointResponse {
    inning: number | null;
    inningType: ApiInningType | null;
    level: number | null;
}

interface BaseGameDetailResponse {
    gameId: number;
    status: GameStatus;
    displayMode: DisplayMode;

    homeTeam: TeamResponse;
    awayTeam: TeamResponse;

    startTime: string | null;
}

/**
 * 예정 경기 응답이다.
 *
 * revealed로 요청해도 백엔드는 항상 PROTECTED를 반환한다.
 */
export interface ScheduledGameDetailResponse
    extends BaseGameDetailResponse {

    status: 'STATUS_SCHEDULED';
    displayMode: 'PROTECTED';

    venue: string | null;
    probablePitchers: ProbablePitchersResponse;
}

/**
 * 진행 경기 보호 응답이다.
 *
 * 점수, 초·말, 현재 타자·투수,
 * 이닝별 점수는 포함하지 않는다.
 */
export interface ProtectedLiveGameDetailResponse
    extends BaseGameDetailResponse {

    status: 'STATUS_IN_PROGRESS';
    displayMode: 'PROTECTED';

    venue: string | null;

    periodLabel: string;
    inning: number | null;
    situation: SituationResponse | null;

    favoritePlayersPlaying: string[];
    switchSuggestion: SwitchSuggestionResponse | null;
}

/**
 * 진행 경기 공개 응답이다.
 */
export interface RevealedLiveGameDetailResponse
    extends BaseGameDetailResponse {

    status: 'STATUS_IN_PROGRESS';
    displayMode: 'REVEALED';

    venue: string | null;

    score: ScoreResponse;
    inning: number | null;
    inningType: ApiInningType | null;

    situation: SituationResponse | null;
    currentMatchup: CurrentMatchupResponse | null;

    favoritePlayersPlaying: string[];
    inningScores: InningScoresResponse;
}

/**
 * 종료 경기 보호 응답이다.
 *
 * 최종 점수, 이닝별 점수, 득점 플레이는 포함하지 않는다.
 */
export interface ProtectedFinalGameDetailResponse
    extends BaseGameDetailResponse {

    status: 'STATUS_FINAL';
    displayMode: 'PROTECTED';

    venue: string | null;
    headline: string | null;
    tensionCurve: ProtectedTensionPointResponse[];
}

/**
 * 종료 경기 공개 응답이다.
 */
export interface RevealedFinalGameDetailResponse
    extends BaseGameDetailResponse {

    status: 'STATUS_FINAL';
    displayMode: 'REVEALED';

    venue: string | null;
    headline: string | null;
    finalScore: ScoreResponse;
    inningScores: InningScoresResponse;
    scoringSummary: ScoringPlayResponse[];
    tensionCurve: RevealedTensionPointResponse[];
}

export type LiveGameDetailResponse =
    | ProtectedLiveGameDetailResponse
    | RevealedLiveGameDetailResponse;

export type FinalGameDetailResponse =
    | ProtectedFinalGameDetailResponse
    | RevealedFinalGameDetailResponse;

export type GameDetailResponse =
    | ScheduledGameDetailResponse
    | LiveGameDetailResponse
    | FinalGameDetailResponse;