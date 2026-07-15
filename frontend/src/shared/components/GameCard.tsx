import { Link } from 'react-router';

import StatusBadge, { type GameStatus } from './StatusBadge';
import TeamMatchup, { type TeamIdentityData } from './TeamMatchup';

// 화면 표시용 경기 카드 데이터.
// API 응답 타입(features/*/api)과 분리해, 각 feature가 응답을 이 형태로 변환해서 넘긴다.
export interface GameCardData {
  gameId: number;
  status: GameStatus;
  awayTeam: TeamIdentityData;
  homeTeam: TeamIdentityData;
  /** 상태별 보조 정보 한 줄: 진행=latestTag, 예정=구장·시각, 종료=헤드라인/keyMoment */
  metaText?: string;
  /** 예정 경기 시작 시각. 상태 배지 안에 표시한다. */
  startTimeText?: string;
  /** 진행 경기의 이닝 표기 (예: "5회") — 초/말은 스포일러라 없음 */
  inningText?: string;
}

interface GameCardProps {
  game: GameCardData;
  /** tile=벤토 그리드·사이드바용 세로 카드, row=하단 목록용 가로 행 */
  variant: 'tile' | 'row';
  /** 팀 로고를 숨겨야 하는 예외 화면에서만 false로 지정한다. */
  showTeamLogos?: boolean;
}

const hoverStyle =
  'transition-all hover:-translate-y-0.5 hover:border-mlb-navy hover:shadow-hover';

function GameCard({ game, variant, showTeamLogos = true }: GameCardProps) {
  const badgeLabel =
    game.status === 'live' && game.inningText
      ? `LIVE · ${game.inningText}`
      : game.status === 'scheduled' && game.startTimeText
        ? `예정 · ${game.startTimeText}`
      : undefined;

  if (variant === 'row') {
    return (
      <Link
        to={`/games/${game.gameId}`}
        state={{ fromCard: true }}
        className={`flex items-center gap-4 rounded-panel border border-card-border bg-white px-5 py-4 shadow-card ${hoverStyle}`}
      >
        <StatusBadge status={game.status} label={badgeLabel} />
        {showTeamLogos ? (
          <TeamMatchup awayTeam={game.awayTeam} homeTeam={game.homeTeam} />
        ) : (
          <span className="font-display text-[15px] font-semibold text-text-strong">
            {game.awayTeam.abbreviation}
            <span className="mx-1.5 text-text-faint">@</span>
            {game.homeTeam.abbreviation}
          </span>
        )}
        {game.metaText && (
          <span className="min-w-0 flex-1 whitespace-pre-line text-sm text-text-muted">
            {game.metaText}
          </span>
        )}
        <span aria-hidden="true" className="ml-auto text-text-faint">
          →
        </span>
      </Link>
    );
  }

  return (
    <Link
      to={`/games/${game.gameId}`}
      state={{ fromCard: true }}
      className={`flex flex-col gap-2.5 rounded-panel border border-card-border bg-white p-5 shadow-card ${hoverStyle}`}
    >
      <div>
        <StatusBadge status={game.status} label={badgeLabel} />
      </div>
      {showTeamLogos ? (
        <TeamMatchup awayTeam={game.awayTeam} homeTeam={game.homeTeam} />
      ) : (
        <span className="font-display text-[17px] font-semibold text-text-strong">
          {game.awayTeam.abbreviation}
          <span className="mx-1.5 text-text-faint">@</span>
          {game.homeTeam.abbreviation}
        </span>
      )}
      {game.metaText && (
        <span className="line-clamp-2 whitespace-pre-line text-[13.5px] text-text-muted">
          {game.metaText}
        </span>
      )}
    </Link>
  );
}

export default GameCard;
