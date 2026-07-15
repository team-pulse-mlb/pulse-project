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

/**
 * 홈 API의 매치업 문자열을 카드 표시용 팀 정보로 변환한다.
 * 올스타 경기는 원본 팀 ID와 약어가 없으므로 공식 MLB 올스타팀 정보로 보완한다.
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
    return {
      abbreviation: trimmedLabel,
      name: trimmedLabel,
      logoUrl: null,
    };
  }

  return {
    abbreviation: team.abbreviation,
    name: team.displayName,
    logoUrl: team.logoUrl,
  };
}
