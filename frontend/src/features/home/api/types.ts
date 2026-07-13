// 홈 API 응답 타입 (backend HomeQueryService record와 1:1).
// 화면 표시용 타입은 shared/components의 GameCardData이며, 변환은 mappers.ts에서 한다.

export interface MatchupResponse {
  home: string;
  away: string;
}

export interface ProbablePitchersResponse {
  home: string | null;
  away: string | null;
}

export interface RankingLiveGameCard {
  gameId: number;
  matchup: MatchupResponse;
  inning: number | null;
  latestTag: string | null;
}

export interface RankingScheduledGameCard {
  gameId: number;
  matchup: MatchupResponse;
  startTime: string;
  venue: string | null;
  probablePitchers: ProbablePitchersResponse | null;
}

export interface RankingFinishedGameCard {
  gameId: number;
  matchup: MatchupResponse;
  headline: string | null;
  keyMoment: string | null;
}

export interface HomeRankingResponse {
  generatedAt: string;
  live: RankingLiveGameCard[];
  scheduled: RankingScheduledGameCard[];
  finished: RankingFinishedGameCard[];
}

export type GameState =
  | 'LIVE'
  | 'FINAL'
  | 'SCHEDULED'
  | 'POSTPONED'
  | 'CANCELED'
  | 'UNKNOWN';

export interface SlateGameCard {
  gameId: number;
  gameState: GameState;
  matchup: MatchupResponse;
  startTime: string | null;
  // 상태별 선택 필드
  inning?: number | null;
  latestTag?: string | null;
  venue?: string | null;
  probablePitchers?: ProbablePitchersResponse | null;
  headline?: string | null;
  keyMoment?: string | null;
}

export interface HomeSlateResponse {
  slateDate: string;
  games: SlateGameCard[];
}

export type SlateStatusFilter = 'all' | 'scheduled' | 'live' | 'finished';
export type SlateSort = 'startTime' | 'recommended';
