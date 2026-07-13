import { useState } from 'react';

import EmptyState from '../../../shared/components/EmptyState';
import GameCard from '../../../shared/components/GameCard';
import SectionHeader from '../../../shared/components/SectionHeader';
import SegmentToggle from '../../../shared/components/SegmentToggle';
import Skeleton from '../../../shared/components/Skeleton';
import { useSse } from '../../../shared/hooks/useSse';

import { toRecommendedCards, toSlateCard } from '../api/mappers';
import type { SlateSort, SlateStatusFilter } from '../api/types';
import DateNavigator from '../components/DateNavigator';
import RecommendedGrid from '../components/RecommendedGrid';
import { useGames, useLiveRankings } from '../hooks/useHomeQueries';

const statusOptions: { value: SlateStatusFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'scheduled', label: '예정' },
  { value: 'live', label: '진행' },
  { value: 'finished', label: '종료' },
];

function HomePage() {
  // SSE 신호 수신 → 랭킹·목록 재조회 (홈이 열려 있는 동안 구독)
  useSse();

  const [date, setDate] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState<SlateStatusFilter>('all');
  const [sort, setSort] = useState<SlateSort>('startTime');

  // 전체 탭은 시작 시각순 고정 (진행 중 상단 고정은 서버가 처리)
  const effectiveSort: SlateSort = status === 'all' ? 'startTime' : sort;

  const rankingsQuery = useLiveRankings();
  const gamesQuery = useGames({ date, status, sort: effectiveSort });

  const recommendedCards = rankingsQuery.data
    ? toRecommendedCards(rankingsQuery.data)
    : undefined;

  const slateDate = gamesQuery.data?.slateDate ?? date;
  const games = gamesQuery.data?.games ?? [];

  const showRecommended =
    rankingsQuery.isLoading || (recommendedCards && recommendedCards.length > 0);

  return (
    <div className="mx-auto max-w-[1120px] px-4 py-8">
      {/* 상단 추천: 추천이 하나도 없으면 영역 자체를 숨긴다 */}
      {showRecommended && (
        <section className="mb-10">
          <SectionHeader title="지금 볼 만한 경기" />
          <RecommendedGrid
            cards={recommendedCards}
            isLoading={rankingsQuery.isLoading}
          />
        </section>
      )}

      <section>
        <SectionHeader title="전체 경기" />

        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <DateNavigator
            slateDate={slateDate}
            onChange={(next) => setDate(next)}
          />

          <div className="flex items-center gap-3">
            <SegmentToggle
              options={statusOptions}
              value={status}
              onChange={setStatus}
              ariaLabel="경기 상태 필터"
            />

            {/* 전체 탭은 시작 시각순 고정 — 드롭다운을 비활성화하고 이유를 안내한다 (USER_FLOW §4.1) */}
            <select
              value={effectiveSort}
              onChange={(event) => setSort(event.target.value as SlateSort)}
              disabled={status === 'all'}
              aria-label="정렬"
              title={status === 'all' ? '전체 탭은 항상 시작 시각순으로 표시됩니다' : undefined}
              className="rounded-[9px] border border-card-border bg-white px-3 py-2 text-sm font-medium text-text-body disabled:opacity-50"
            >
              <option value="recommended">추천순</option>
              <option value="startTime">시작 시각순</option>
            </select>
          </div>
        </div>

        {gamesQuery.isLoading ? (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-28" />
            ))}
          </div>
        ) : gamesQuery.isError ? (
          <EmptyState message="경기 목록을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요." />
        ) : games.length === 0 ? (
          <EmptyState message="이 날짜에는 예정된 경기가 없어요." />
        ) : (
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {games.map((game) => (
              <li key={game.gameId} className="grid">
                <GameCard game={toSlateCard(game)} variant="tile" />
              </li>
            ))}
          </ul>
        )}

      </section>
    </div>
  );
}

export default HomePage;
