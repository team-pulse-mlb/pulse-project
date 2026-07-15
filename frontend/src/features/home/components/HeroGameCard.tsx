import { Link } from 'react-router';

import type { GameCardData } from '../../../shared/components/GameCard';
import StatusBadge from '../../../shared/components/StatusBadge';
import TeamMatchup from '../../../shared/components/TeamMatchup';

// 상단 추천 1순위 히어로 카드 (bento 그리드에서 가로·세로 2배).
function HeroGameCard({ game }: { game: GameCardData }) {
  const badgeLabel =
    game.status === 'live' && game.inningText
      ? `LIVE · ${game.inningText}`
      : game.status === 'scheduled' && game.startTimeText
        ? `예정 · ${game.startTimeText}`
      : undefined;

  return (
    <Link
      to={`/games/${game.gameId}`}
      state={{ fromCard: true }}
      className="relative flex h-full min-h-44 flex-col overflow-hidden rounded-hero bg-gradient-to-br from-[#0B2559] to-[#04122E] p-7 shadow-hero transition-transform hover:-translate-y-0.5 md:min-h-[280px]"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-20 right-0 h-56 w-56 rounded-full bg-mlb-red/15 blur-3xl"
      />

      <div className="relative">
        <StatusBadge status={game.status} label={badgeLabel} />
      </div>

      <TeamMatchup
        awayTeam={game.awayTeam}
        homeTeam={game.homeTeam}
        size="hero"
        tone="dark"
        className="absolute inset-x-4 top-1/2 -translate-y-1/2 sm:inset-x-7"
      />

      {game.metaText && (
        <span className="absolute bottom-7 left-7 rounded-full bg-white/10 px-3.5 py-1.5 text-sm font-semibold text-white">
          {game.metaText}
        </span>
      )}
    </Link>
  );
}

export default HeroGameCard;
