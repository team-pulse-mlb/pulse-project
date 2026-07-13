import { Link } from 'react-router';

import type { GameCardData } from '../../../shared/components/GameCard';
import StatusBadge from '../../../shared/components/StatusBadge';

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
      className="relative flex h-full flex-col justify-between overflow-hidden rounded-hero bg-gradient-to-br from-[#0B2559] to-[#04122E] p-7 shadow-hero transition-transform hover:-translate-y-0.5"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-20 right-0 h-56 w-56 rounded-full bg-mlb-red/15 blur-3xl"
      />

      <div className="relative">
        <StatusBadge status={game.status} label={badgeLabel} />
      </div>

      <div className="relative mt-8">
        <p className="font-display text-[44px] font-bold leading-tight text-white">
          {game.awayLabel}
          <span className="mx-3 text-[28px] text-white/40">@</span>
          {game.homeLabel}
        </p>

        {game.metaText && (
          <span className="mt-4 inline-block rounded-full bg-white/10 px-3.5 py-1.5 text-sm font-semibold text-white">
            {game.metaText}
          </span>
        )}
      </div>
    </Link>
  );
}

export default HeroGameCard;
