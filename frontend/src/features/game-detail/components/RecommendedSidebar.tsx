import GameCard from '../../../shared/components/GameCard';
import SectionHeader from '../../../shared/components/SectionHeader';
import { toRecommendedCards } from '../../home/api/mappers';
import { useLiveRankings } from '../../home/hooks/useHomeQueries';

interface RecommendedSidebarProps {
  /** 현재 보고 있는 경기 (목록에서 제외) */
  currentGameId: number;
}

// 상세 화면 우측 추천 사이드바 (USER_FLOW §3.5).
// 홈 상단 추천과 같은 데이터를 쓰고, 현재 경기만 제외한다. 데스크톱 전용.
function RecommendedSidebar({ currentGameId }: RecommendedSidebarProps) {
  const rankingsQuery = useLiveRankings();

  const cards = rankingsQuery.data
    ? toRecommendedCards(rankingsQuery.data).filter(
        (card) => card.gameId !== currentGameId,
      )
    : [];

  if (cards.length === 0) {
    return null;
  }

  return (
    <aside className="sticky top-[86px] hidden lg:block">
      <SectionHeader title="다른 볼 만한 경기" />
      <div className="flex flex-col gap-3">
        {cards.slice(0, 5).map((card) => (
          <GameCard key={card.gameId} game={card} variant="tile" />
        ))}
      </div>
    </aside>
  );
}

export default RecommendedSidebar;
