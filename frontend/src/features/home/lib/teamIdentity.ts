import type { TeamResponse } from '../../../shared/api/teamApi';
import type { TeamIdentityData } from '../../../shared/components/TeamMatchup';

const ALL_STAR_TEAMS: Record<string, Omit<TeamIdentityData, 'name'>> = {
  'american all-stars': {
    abbreviation: 'AL',
    logoUrl: 'https://www.mlbstatic.com/team-logos/159.svg',
  },
  'american league all-stars': {
    abbreviation: 'AL',
    logoUrl: 'https://www.mlbstatic.com/team-logos/159.svg',
  },
  'national all-stars': {
    abbreviation: 'NL',
    logoUrl: 'https://www.mlbstatic.com/team-logos/160.svg',
  },
  'national league all-stars': {
    abbreviation: 'NL',
    logoUrl: 'https://www.mlbstatic.com/team-logos/160.svg',
  },
};

const MLB_LOGO_TEAM_IDS: Record<string, number> = {
  ARI: 109,
  ATL: 144,
  BAL: 110,
  BOS: 111,
  CHC: 112,
  CHW: 145,
  CIN: 113,
  CLE: 114,
  COL: 115,
  DET: 116,
  HOU: 117,
  KC: 118,
  LAA: 108,
  LAD: 119,
  MIA: 146,
  MIL: 158,
  MIN: 142,
  NYM: 121,
  NYY: 147,
  OAK: 133,
  PHI: 143,
  PIT: 134,
  SD: 135,
  SEA: 136,
  SF: 137,
  STL: 138,
  TB: 139,
  TEX: 140,
  TOR: 141,
  WSH: 120,
};

/**
 * 홈 API의 매치업 문자열을 카드 표시용 팀 정보로 변환한다.
 * 올스타 경기는 원본 팀 ID와 약어가 없으므로 공식 MLB 올스타팀 정보로 보완한다.
 * 팀 목록을 함께 조회하지 않는 상세 추천 카드도 약어 기준 공식 로고를 표시한다.
 */
export function toTeamIdentity(
  label: string,
  teams: readonly TeamResponse[] | undefined,
): TeamIdentityData {
  const trimmedLabel = label.trim();
  const allStarTeam = ALL_STAR_TEAMS[trimmedLabel.toLowerCase()];
  if (allStarTeam) {
    return {
      ...allStarTeam,
      name: trimmedLabel,
    };
  }

  const normalizedLabel = trimmedLabel.toLowerCase();
  const team = teams?.find((candidate) =>
    [candidate.abbreviation, candidate.displayName, candidate.shortDisplayName]
      .some((value) =>
        typeof value === 'string'
        && value.trim().toLowerCase() === normalizedLabel
      ),
  );

  if (!team) {
    const normalizedAbbreviation = trimmedLabel.toUpperCase();
    const logoTeamId = MLB_LOGO_TEAM_IDS[normalizedAbbreviation];

    return {
      abbreviation: logoTeamId
        ? normalizedAbbreviation
        : trimmedLabel,
      name: trimmedLabel,
      logoUrl: logoTeamId
        ? `https://www.mlbstatic.com/team-logos/${logoTeamId}.svg`
        : null,
    };
  }

  return {
    abbreviation: team.abbreviation,
    name: team.displayName,
    logoUrl: team.logoUrl,
  };
}
