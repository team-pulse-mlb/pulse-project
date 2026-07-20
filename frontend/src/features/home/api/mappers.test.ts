import { describe, expect, it } from 'vitest';

import type { TeamResponse } from '../../../shared/api/teamApi';

import { toRecommendedCards, toSlateCard } from './mappers';
import type { HomeRankingResponse, SlateGameCard } from './types';

const teams: TeamResponse[] = [
  {
    teamId: 119,
    logoTeamId: 119,
    abbreviation: 'LAD',
    displayName: 'Los Angeles Dodgers',
    shortDisplayName: 'Dodgers',
    league: 'National',
    division: 'West',
    logoUrl: 'https://example.com/lad.svg',
  },
  {
    teamId: 147,
    logoTeamId: 147,
    abbreviation: 'NYY',
    displayName: 'New York Yankees',
    shortDisplayName: 'Yankees',
    league: 'American',
    division: 'East',
    logoUrl: 'https://example.com/nyy.svg',
  },
];

const baseSlateCard: SlateGameCard = {
  gameId: 1,
  gameState: 'SCHEDULED',
  matchup: { away: 'LAD', home: 'NYY' },
  startTime: null,
};

describe('toSlateCard', () => {
  it.each([
    {
      설명: '진행 경기는 이닝과 최신 태그를 표시한다',
      card: { gameState: 'LIVE', inning: 7, latestTag: '투수 교체' },
      expected: { status: 'live', inningText: '7회', metaText: '투수 교체' },
    },
    {
      설명: '종료 경기는 헤드라인이 없으면 주요 장면을 사용한다',
      card: { gameState: 'FINAL', headline: null, keyMoment: '9회 결정적 장면' },
      expected: { status: 'final', metaText: '9회 결정적 장면' },
    },
    {
      설명: '연기 경기는 구장을 보조 정보로 유지한다',
      card: { gameState: 'POSTPONED', venue: 'Yankee Stadium' },
      expected: { status: 'postponed', metaText: 'Yankee Stadium' },
    },
    {
      설명: '취소 경기는 누락된 구장을 안전하게 생략한다',
      card: { gameState: 'CANCELED', venue: null },
      expected: { status: 'canceled', metaText: undefined },
    },
    {
      설명: '예정 경기는 한쪽 선발 누락을 미정으로 표시한다',
      card: {
        gameState: 'SCHEDULED',
        venue: 'Dodger Stadium',
        probablePitchers: { away: 'Yamamoto', home: null },
      },
      expected: {
        status: 'scheduled',
        startTimeText: undefined,
        metaText: 'Dodger Stadium\n선발 Yamamoto · 미정',
      },
    },
    {
      설명: '알 수 없는 상태는 예정 경기 폴백을 사용한다',
      card: { gameState: 'UNKNOWN', probablePitchers: null },
      expected: { status: 'scheduled', metaText: '선발 미확정' },
    },
  ] satisfies Array<{
    설명: string;
    card: Partial<SlateGameCard> & Pick<SlateGameCard, 'gameState'>;
    expected: Record<string, unknown>;
  }>)('$설명', ({ card, expected }) => {
    const result = toSlateCard({ ...baseSlateCard, ...card }, teams);

    expect(result).toMatchObject(expected);
    expect(result.awayTeam).toMatchObject({ abbreviation: 'LAD' });
    expect(result.homeTeam).toMatchObject({ abbreviation: 'NYY' });
  });
});

describe('toRecommendedCards', () => {
  it('진행, 종료, 예정 순서를 유지하면서 최대 다섯 장만 반환한다', () => {
    const response: HomeRankingResponse = {
      generatedAt: '2026-07-20T00:00:00Z',
      live: [1, 2].map((gameId) => ({
        gameId,
        matchup: { away: 'LAD', home: 'NYY' },
        inning: null,
        latestTag: null,
      })),
      finished: [3, 4].map((gameId) => ({
        gameId,
        matchup: { away: 'LAD', home: 'NYY' },
        headline: null,
        keyMoment: null,
      })),
      scheduled: [5, 6].map((gameId) => ({
        gameId,
        matchup: { away: 'LAD', home: 'NYY' },
        startTime: '2026-07-20T18:00:00Z',
        venue: null,
        probablePitchers: null,
      })),
    };

    expect(toRecommendedCards(response, teams).map(({ gameId, status }) => ({ gameId, status })))
      .toEqual([
        { gameId: 1, status: 'live' },
        { gameId: 2, status: 'live' },
        { gameId: 3, status: 'final' },
        { gameId: 4, status: 'final' },
        { gameId: 5, status: 'scheduled' },
      ]);
  });
});
