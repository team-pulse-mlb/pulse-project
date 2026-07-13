import { useParams } from 'react-router';

import EmptyState from '../../../shared/components/EmptyState';
import RecommendedSidebar from '../components/RecommendedSidebar';

// 경기 상세 페이지 골격.
// 본문 조립(예정/진행/종료 × 보호/공개)은 local-docs/CODEX_FRONTEND_PROMPT.md의
// 위임 명세에 따라 구현한다. 완성 후 데이터 연결은 민석 담당.
function GameDetailPage() {
  const { gameId } = useParams<{ gameId: string }>();

  if (!gameId) {
    return (
      <div className="mx-auto max-w-[1160px] px-4 py-8">
        <EmptyState message="경기를 찾을 수 없습니다." />
      </div>
    );
  }

  return (
    <div className="mx-auto grid max-w-[1160px] grid-cols-1 gap-10 px-4 py-8 lg:grid-cols-[minmax(0,1fr)_336px]">
      <div className="flex flex-col gap-5">
        <EmptyState message="경기 상세 화면 구현 예정" />
      </div>

      <RecommendedSidebar currentGameId={Number(gameId)} />
    </div>
  );
}

export default GameDetailPage;
