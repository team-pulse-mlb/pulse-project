import type { GameCardData } from '../../../shared/components/GameCard';
import type { TeamResponse } from '../../../shared/api/teamApi';
import { formatInning, formatStartTime } from '../../../shared/lib/format';
import { toTeamIdentity } from '../lib/teamIdentity';

import type {
  HomeRankingResponse,
  RankingFinishedGameCard,
  RankingLiveGameCard,
  RankingScheduledGameCard,
  SlateGameCard,
} from './types';

// API 응답 → 화면 표시용 GameCardData 변환.
// 상태별 보조 문구 규칙: 진행=latestTag 1개, 예정=구장·시각(태그 없음), 종료=headline/keyMoment.

function fromLive(
  card: RankingLiveGameCard,
  teams?: readonly TeamResponse[],
): GameCardData {
  return {
    gameId: card.gameId,
    status: 'live',
    awayTeam: toTeamIdentity(card.matchup.away, teams),
    homeTeam: toTeamIdentity(card.matchup.home, teams),
    inningText: formatInning(card.inning) ?? undefined,
    metaText: card.latestTag ?? undefined,
  };
}

function fromScheduled(
  card: RankingScheduledGameCard,
  teams?: readonly TeamResponse[],
): GameCardData {
  const parts = [card.venue, probablePitchersText(card.probablePitchers)].filter(Boolean);

  return {
    gameId: card.gameId,
    status: 'scheduled',
    awayTeam: toTeamIdentity(card.matchup.away, teams),
    homeTeam: toTeamIdentity(card.matchup.home, teams),
    startTimeText: formatStartTime(card.startTime),
    metaText: parts.join(' · ') || undefined,
  };
}

function fromFinished(
  card: RankingFinishedGameCard,
  teams?: readonly TeamResponse[],
): GameCardData {
  return {
    gameId: card.gameId,
    status: 'final',
    awayTeam: toTeamIdentity(card.matchup.away, teams),
    homeTeam: toTeamIdentity(card.matchup.home, teams),
    metaText: card.headline ?? card.keyMoment ?? undefined,
  };
}

/** 상단 추천: 진행→종료→예정 순으로 슬롯을 채워 최대 5장 */
export function toRecommendedCards(
  response: HomeRankingResponse,
  teams?: readonly TeamResponse[],
): GameCardData[] {
  return [
    ...response.live.map((card) => fromLive(card, teams)),
    ...response.finished.map((card) => fromFinished(card, teams)),
    ...response.scheduled.map((card) => fromScheduled(card, teams)),
  ].slice(0, 5);
}

export function toSlateCard(
  card: SlateGameCard,
  teams?: readonly TeamResponse[],
): GameCardData {
  if (card.gameState === 'LIVE') {
    return {
      gameId: card.gameId,
      status: 'live',
      awayTeam: toTeamIdentity(card.matchup.away, teams),
      homeTeam: toTeamIdentity(card.matchup.home, teams),
      inningText: formatInning(card.inning) ?? undefined,
      metaText: card.latestTag ?? undefined,
    };
  }

  if (card.gameState === 'FINAL') {
    return {
      gameId: card.gameId,
      status: 'final',
      awayTeam: toTeamIdentity(card.matchup.away, teams),
      homeTeam: toTeamIdentity(card.matchup.home, teams),
      metaText: card.headline ?? card.keyMoment ?? undefined,
    };
  }

  if (card.gameState === 'POSTPONED' || card.gameState === 'CANCELED') {
    return {
      gameId: card.gameId,
      status: card.gameState === 'POSTPONED' ? 'postponed' : 'canceled',
      awayTeam: toTeamIdentity(card.matchup.away, teams),
      homeTeam: toTeamIdentity(card.matchup.home, teams),
      metaText: card.venue ?? undefined,
    };
  }

  const parts = [card.venue, probablePitchersText(card.probablePitchers)].filter(Boolean);

  return {
    gameId: card.gameId,
    status: 'scheduled',
    awayTeam: toTeamIdentity(card.matchup.away, teams),
    homeTeam: toTeamIdentity(card.matchup.home, teams),
    startTimeText: card.startTime ? formatStartTime(card.startTime) : undefined,
    metaText: parts.join(' · ') || undefined,
  };
}

function probablePitchersText(
  probablePitchers: RankingScheduledGameCard['probablePitchers'] | undefined,
): string {
  const away = probablePitchers?.away;
  const home = probablePitchers?.home;
  if (!away && !home) {
    return '선발 미확정';
  }
  return `선발 ${away ?? '미정'} · ${home ?? '미정'}`;
}
