import { describe, expect, it } from 'vitest';

import type { TeamResponse } from '../../../shared/api/teamApi';

import { toTeamIdentity } from './teamIdentity';

const dodgers: TeamResponse = {
  teamId: 119,
  logoTeamId: 119,
  abbreviation: 'LAD',
  displayName: 'Los Angeles Dodgers',
  shortDisplayName: 'Dodgers',
  league: 'National',
  division: 'West',
  logoUrl: 'https://example.com/lad.svg',
};

describe('toTeamIdentity', () => {
  it.each([
    {
      설명: '약어의 대소문자와 공백을 정규화해 찾는다',
      label: ' lad ',
      teams: [dodgers],
      expected: {
        abbreviation: 'LAD',
        name: 'Los Angeles Dodgers',
        logoUrl: 'https://example.com/lad.svg',
      },
    },
    {
      설명: '전체 팀 이름으로 찾는다',
      label: 'los angeles dodgers',
      teams: [dodgers],
      expected: {
        abbreviation: 'LAD',
        name: 'Los Angeles Dodgers',
        logoUrl: 'https://example.com/lad.svg',
      },
    },
    {
      설명: '짧은 팀 이름으로 찾는다',
      label: 'DODGERS',
      teams: [dodgers],
      expected: {
        abbreviation: 'LAD',
        name: 'Los Angeles Dodgers',
        logoUrl: 'https://example.com/lad.svg',
      },
    },
    {
      설명: '올스타 팀 이름은 팀 목록 없이 공식 리그 로고로 변환한다',
      label: ' American League All-Stars ',
      teams: undefined,
      expected: {
        abbreviation: 'AL',
        name: 'American League All-Stars',
        logoUrl: 'https://www.mlbstatic.com/team-logos/159.svg',
      },
    },
    {
      설명: '목록에 없는 공식 약어는 MLB 로고 ID 폴백을 사용한다',
      label: 'nyy',
      teams: [],
      expected: {
        abbreviation: 'NYY',
        name: 'nyy',
        logoUrl: 'https://www.mlbstatic.com/team-logos/147.svg',
      },
    },
    {
      설명: '알 수 없는 팀은 입력값을 유지하고 로고를 비운다',
      label: ' Mystery Club ',
      teams: [],
      expected: {
        abbreviation: 'Mystery Club',
        name: 'Mystery Club',
        logoUrl: null,
      },
    },
  ])('$설명', ({ label, teams, expected }) => {
    expect(toTeamIdentity(label, teams)).toEqual(expected);
  });
});
