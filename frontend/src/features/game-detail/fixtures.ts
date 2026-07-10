// 셸 확인용 더미 — 데이터 연결 시 삭제

import type { BoxScoreLine } from '../../shared/components/BoxScoreTable';
import type { GameCardData } from '../../shared/components/GameCard';
import type { Situation } from './components/CurrentSituationCard';
import type { TimelineEvent } from './components/EventTimeline';
import type { TensionPoint } from './components/TensionCurve';

export type FixtureGameStatus = 'SCHEDULED' | 'LIVE' | 'FINAL';

interface TeamFixture {
  abbr: string;
  name: string;
}

export interface GameDetailFixture {
  status: FixtureGameStatus;
  awayTeam: TeamFixture;
  homeTeam: TeamFixture;
  startTime: string;
  venue: string;
  probablePitchers: { away: string | null; home: string | null };
  inning: number;
  inningType: 'TOP' | 'BOTTOM';
  awayScore: number;
  homeScore: number;
  situation: Situation | null;
  currentMatchup: { batter: string; pitcher: string; favorite: 'batter' | 'pitcher' | null } | null;
  protectedHeadline: string | null;
  revealedHeadline: string | null;
  protectedEvents: TimelineEvent[];
  revealedEvents: TimelineEvent[];
  recentPlays: TimelineEvent[];
  protectedCurve: TensionPoint[];
  revealedCurve: TensionPoint[];
  awayLine: BoxScoreLine;
  homeLine: BoxScoreLine;
  scoringPlays: { inningLabel: string; text: string; score: string }[];
}

const awayLine: BoxScoreLine = {
  abbr: 'SD', innings: [0, 1, 0, 2, 0, 0, 0, 1, null], runs: 4, hits: 8, errors: 1,
};
const homeLine: BoxScoreLine = {
  abbr: 'CHC', innings: [0, 0, 1, 0, 2, 0, 0, null, null], runs: 3, hits: 7, errors: 0,
};

const protectedEvents: TimelineEvent[] = [
  { eventId: 1, inningLabel: '1회', text: '승부처 카운트가 이어졌습니다.' },
  { eventId: 2, inningLabel: '2회', text: '삼자범퇴로 이닝을 마쳤습니다.' },
  { eventId: 3, inningLabel: '3회', text: '득점권 압박이 이어졌습니다.' },
  { eventId: 4, inningLabel: '5회', text: '긴 접전 승부가 이어졌습니다.' },
];

const revealedEvents: TimelineEvent[] = [
  { eventId: 11, inningLabel: '1회 초', text: '선두 타자가 볼넷으로 출루했습니다.' },
  { eventId: 12, inningLabel: '2회 말', text: '삼자범퇴로 이닝을 마쳤습니다.' },
  { eventId: 13, inningLabel: '4회 초', text: '2루타로 SD가 2점을 앞서 나갔습니다.' },
  { eventId: 14, inningLabel: '5회 말', text: 'CHC가 적시타로 3-3 동점을 만들었습니다.' },
];

const baseFixture: GameDetailFixture = {
  status: 'LIVE',
  awayTeam: { abbr: 'SD', name: 'San Diego' },
  homeTeam: { abbr: 'CHC', name: 'Chicago Cubs' },
  startTime: '2026-07-09T08:05:00+09:00',
  venue: 'Wrigley Field, Chicago',
  probablePitchers: { away: 'Dylan Cease', home: null },
  inning: 8,
  inningType: 'TOP',
  awayScore: 4,
  homeScore: 3,
  situation: {
    balls: 3, strikes: 2, outs: 2,
    runnerOnFirst: false, runnerOnSecond: true, runnerOnThird: false,
    scoringPosition: true, basesLoaded: false,
  },
  currentMatchup: { batter: 'Ha-Seong Kim', pitcher: 'Justin Steele', favorite: 'batter' },
  protectedHeadline: '다시 볼 만한 흐름이 끝까지 이어진 경기입니다.',
  revealedHeadline: 'CHC가 8회 대량 득점으로 SD를 5-3으로 제압했습니다.',
  protectedEvents,
  revealedEvents,
  recentPlays: [
    { eventId: 21, inningLabel: '8회 초', text: '스트라이크아웃, 2아웃' },
    { eventId: 22, inningLabel: '8회 초', text: '1점 적시 2루타', highlighted: true },
    { eventId: 23, inningLabel: '7회 말', text: '병살타로 위기 탈출' },
  ],
  protectedCurve: [
    { inning: 1, level: 2 }, { inning: 2, level: 4, eventLabel: '긴 접전 승부' },
    { inning: 3, level: 3 }, { inning: 4, level: 4, eventLabel: '득점권 승부' },
    { inning: 5, level: 5, eventLabel: '만루 승부' }, { inning: 6, level: 3 },
    { inning: 7, level: 4, eventLabel: '흐름 급변' }, { inning: 8, level: 5, eventLabel: '강한 타구' },
    { inning: 9, level: 4 },
  ],
  revealedCurve: [
    { inning: 1, inningType: 'TOP', level: 2 }, { inning: 1, inningType: 'BOTTOM', level: 3 },
    { inning: 2, inningType: 'TOP', level: 4, eventLabel: '솔로 홈런' },
    { inning: 2, inningType: 'BOTTOM', level: 2 }, { inning: 3, inningType: 'TOP', level: 3 },
    { inning: 3, inningType: 'BOTTOM', level: 4, eventLabel: '동점 적시타' },
    { inning: 4, inningType: 'TOP', level: 5, eventLabel: '2점 2루타' },
    { inning: 4, inningType: 'BOTTOM', level: 3 }, { inning: 5, inningType: 'TOP', level: 4 },
    { inning: 5, inningType: 'BOTTOM', level: 5, eventLabel: '리드 변경' },
  ],
  awayLine,
  homeLine,
  scoringPlays: [
    { inningLabel: '2회 초', text: '솔로 홈런', score: 'SD 1-0' },
    { inningLabel: '3회 말', text: '적시타', score: '1-1' },
    { inningLabel: '4회 초', text: '2점 2루타', score: 'SD 3-1' },
    { inningLabel: '8회 말', text: '3점 홈런', score: 'CHC 5-3' },
  ],
};

const scheduledFixture: GameDetailFixture = {
  ...baseFixture,
  status: 'SCHEDULED',
  awayTeam: { abbr: 'BOS', name: 'Boston' },
  homeTeam: { abbr: 'NYY', name: 'New York' },
  venue: 'Yankee Stadium, New York',
  probablePitchers: { away: null, home: null },
};

const finalFixture: GameDetailFixture = {
  ...baseFixture,
  status: 'FINAL',
  awayTeam: { abbr: 'SF', name: 'San Francisco' },
  homeTeam: { abbr: 'LAD', name: 'Los Angeles' },
  venue: 'Dodger Stadium, Los Angeles',
  awayScore: 3,
  homeScore: 5,
  awayLine: { ...awayLine, abbr: 'SF', innings: [0, 1, 0, 2, 0, 0, 0, 0, 0], runs: 3 },
  homeLine: { ...homeLine, abbr: 'LAD', innings: [0, 0, 1, 0, 1, 0, 0, 3, null], runs: 5, hits: 10 },
};

// 개발 확인용 분기: gameId 끝자리 0=예정, 1=종료, 그 외=진행.
export function getGameDetailFixture(gameId: string): GameDetailFixture {
  if (gameId.endsWith('0')) return scheduledFixture;
  if (gameId.endsWith('1')) return finalFixture;
  return baseFixture;
}

export const recommendedGameFixtures: GameCardData[] = [
  { gameId: 30, status: 'scheduled', awayLabel: 'BOS', homeLabel: 'NYY', startTimeText: '23:05', metaText: 'Yankee Stadium · 선발 미확정' },
  { gameId: 32, status: 'live', awayLabel: 'HOU', homeLabel: 'SEA', inningText: '5회', metaText: '접전 흐름' },
  { gameId: 31, status: 'final', awayLabel: 'ATL', homeLabel: 'NYM', metaText: '후반 긴장 구간이 이어진 경기' },
];
