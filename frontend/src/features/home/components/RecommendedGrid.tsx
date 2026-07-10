import type { GameCardData } from '../../../shared/components/GameCard';
import GameCard from '../../../shared/components/GameCard';
import Skeleton from '../../../shared/components/Skeleton';

import HeroGameCard from './HeroGameCard';

interface RecommendedGridProps {
  cards: GameCardData[] | undefined;
  isLoading: boolean;
}

// 상단 추천 bento 그리드: 1순위 히어로(2×2) + 소형 카드 최대 4장.
// 추천이 없으면 영역 전체를 렌더링하지 않는다 (호출부에서 처리).
function RecommendedGrid({ cards, isLoading }: RecommendedGridProps) {
  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 md:grid-cols-[2fr_1fr_1fr] md:grid-rows-2">
        <Skeleton className="col-span-2 h-44 md:col-span-1 md:row-span-2 md:h-auto md:min-h-[280px]" />
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="h-32" />
        ))}
      </div>
    );
  }

  if (!cards || cards.length === 0) {
    return null;
  }

  const [hero, ...rest] = cards;

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-[2fr_1fr_1fr] md:grid-rows-2">
      <div className="col-span-2 md:col-span-1 md:row-span-2">
        <HeroGameCard game={hero} />
      </div>
      {rest.map((card) => (
        <GameCard key={card.gameId} game={card} variant="tile" />
      ))}
    </div>
  );
}

export default RecommendedGrid;
