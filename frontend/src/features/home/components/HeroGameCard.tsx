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
      className="relative flex h-full min-h-[232px] flex-col overflow-hidden rounded-hero bg-gradient-to-br from-[#0B2559] to-[#04122E] p-5 shadow-hero transition-transform hover:-translate-y-0.5 sm:p-7 md:min-h-[280px]"
    >
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-20 right-0 h-56 w-56 rounded-full bg-mlb-red/15 blur-3xl"
      />

      <div className="relative">
        <StatusBadge status={game.status} label={badgeLabel} />
      </div>

      <div className="relative flex flex-1 items-center py-5">
        <TeamMatchup
          awayTeam={game.awayTeam}
          homeTeam={game.homeTeam}
          size="hero"
          tone="dark"
        />
      </div>

      {game.metaText && (
        <span className="relative line-clamp-2 max-w-full self-start rounded-2xl bg-white/10 px-3.5 py-1.5 text-sm font-semibold leading-5 text-white">
          {game.metaText}
        </span>
      )}
    </Link>
  );
}

export default HeroGameCard;
