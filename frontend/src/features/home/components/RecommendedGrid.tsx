import type { GameCardData } from '../../../shared/components/GameCard';
import GameCard from '../../../shared/components/GameCard';
import Skeleton from '../../../shared/components/Skeleton';

import useFlipAnimation from '../hooks/useFlipAnimation';
import HeroGameCard from './HeroGameCard';

interface RecommendedGridProps {
  cards: GameCardData[] | undefined;
  isLoading: boolean;
}

// 상단 추천 bento 그리드: 1순위 히어로(2×2) + 소형 카드 최대 4장.
// 추천이 없으면 영역 전체를 렌더링하지 않는다 (호출부에서 처리).
function RecommendedGrid({ cards, isLoading }: RecommendedGridProps) {
  // 카드 순서가 바뀔 때만 FLIP을 실행하도록 순서 키를 넘긴다.
  const orderKey = cards?.map((card) => card.gameId).join(',') ?? '';
  const gridRef = useFlipAnimation(orderKey);

  if (isLoading) {
    return (
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-[2fr_1fr_1fr] md:grid-rows-2">
        <Skeleton className="h-44 sm:col-span-2 md:col-span-1 md:row-span-2 md:h-auto md:min-h-[280px]" />
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
    <div
      ref={gridRef}
      className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-[2fr_1fr_1fr] md:grid-rows-2"
    >
      <div
        className="sm:col-span-2 md:col-span-1 md:row-span-2"
        data-flip-id={hero.gameId}
      >
        <HeroGameCard game={hero} />
      </div>
      {/* 래퍼가 그리드 아이템이 되므로 내부 카드가 셀을 가득 채우도록 grid로 늘린다 */}
      {rest.map((card) => (
        <div key={card.gameId} className="grid" data-flip-id={card.gameId}>
          <GameCard game={card} variant="tile" showTeamLogos />
        </div>
      ))}
    </div>
  );
}

export default RecommendedGrid;
