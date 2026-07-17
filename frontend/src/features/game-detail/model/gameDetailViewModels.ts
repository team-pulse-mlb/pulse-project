import type { DisplayMode } from '../lib/displayMode';

export type ViewInningType = 'TOP' | 'BOTTOM';

export interface GameDetailTeamViewModel {
    name: string;
    abbr: string;
    logoUrl: string | null;
}

export interface GameDateViewModel {
    /**
     * 시작 시각이 없으면 null이다.
     * 화면에서 임의 연도를 만들어 표시하지 않는다.
     */
    season: number | null;

    /**
     * 사용자 로컬 시간 기준 M/D 표기다.
     */
    dateLabel: string | null;

    /**
     * 예정 경기 상단 메타 정보에서 사용하는 시작 시각이다.
     */
    startTimeLabel: string | null;
}

export interface GameSituationViewModel {
    balls: number | null;
    strikes: number | null;
    outs: number | null;

    runnerOnFirst: boolean;
    runnerOnSecond: boolean;
    runnerOnThird: boolean;

    scoringPosition: boolean;
    basesLoaded: boolean;
}

export interface GamePlayerViewModel {
    id: number;
    name: string;
}

export interface CurrentMatchupViewModel {
    batter: GamePlayerViewModel;
    pitcher: GamePlayerViewModel;
}

export interface GameBoxScoreLineViewModel {
    abbr: string;
    innings: Array<number | null>;
    runs: number | null;
}

export interface GameInningScoresViewModel {
    awayLine: GameBoxScoreLineViewModel;
    homeLine: GameBoxScoreLineViewModel;
}

export type TensionLevel =
    | 1
    | 2
    | 3
    | 4
    | 5;

export interface GameTensionPointViewModel {
    inning: number;
    inningType?: ViewInningType;
    level: TensionLevel;
}

export interface ScoringPlayViewModel {
    eventId: number;
    inning: number;
    inningType?: ViewInningType;
    text: string;
}

export interface StartingLineupPlayerViewModel {
    battingOrder: number;
    playerName: string;
    position: string | null;
}

export interface ScheduledGameDetailViewModel
    extends GameDateViewModel {

    kind: 'SCHEDULED';

    gameId: number;
    status: 'STATUS_SCHEDULED';
    displayMode: 'PROTECTED';

    homeTeam: GameDetailTeamViewModel;
    awayTeam: GameDetailTeamViewModel;

    probablePitchers: {
        home: string | null;
        away: string | null;
    };

    startingLineups: {
        home: StartingLineupPlayerViewModel[];
        away: StartingLineupPlayerViewModel[];
    };
}

export interface LiveGameDetailViewModel
    extends GameDateViewModel {

    kind: 'LIVE';

    gameId: number;
    status: 'STATUS_IN_PROGRESS';
    displayMode: DisplayMode;

    homeTeam: GameDetailTeamViewModel;
    awayTeam: GameDetailTeamViewModel;

    /**
     * 현재 상세 API 계약에는 진행 경기 구장이 없다.
     */
    venue: string | null;

    inning: number | null;
    inningType: ViewInningType | null;

    homeScore: number | null;
    awayScore: number | null;

    situation: GameSituationViewModel | null;
    currentMatchup: CurrentMatchupViewModel | null;

    inningScores: GameInningScoresViewModel | null;

    favoritePlayersPlaying: string[];
}

export interface FinalGameDetailViewModel
    extends GameDateViewModel {

    kind: 'FINAL';

    gameId: number;
    status: 'STATUS_FINAL';
    displayMode: DisplayMode;

    homeTeam: GameDetailTeamViewModel;
    awayTeam: GameDetailTeamViewModel;

    /**
     * 현재 상세 API 계약에는 종료 경기 구장이 없다.
     */
    venue: string | null;

    headline: string | null;

    homeScore: number | null;
    awayScore: number | null;

    inningScores: GameInningScoresViewModel | null;
    tensionPoints: GameTensionPointViewModel[];
    scoringPlays: ScoringPlayViewModel[];
}

export type GameDetailViewModel =
    | ScheduledGameDetailViewModel
    | LiveGameDetailViewModel
    | FinalGameDetailViewModel;