import { useState } from 'react';
import { useSearchParams } from 'react-router';

import EmptyState from '../../../shared/components/EmptyState';
import GameCard from '../../../shared/components/GameCard';
import SectionHeader from '../../../shared/components/SectionHeader';
import SegmentToggle from '../../../shared/components/SegmentToggle';
import Skeleton from '../../../shared/components/Skeleton';
import { useSse } from '../../../shared/hooks/useSse';
import { todaySlateDate } from '../../../shared/lib/format';

import { toRecommendedCards, toSlateCard } from '../api/mappers';
import type { SlateSort, SlateStatusFilter } from '../api/types';
import DateNavigator from '../components/DateNavigator';
import RecommendedGrid from '../components/RecommendedGrid';
import { useGames, useLiveRankings, useTeamCatalog } from '../hooks/useHomeQueries';

const statusOptions: { value: SlateStatusFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'scheduled', label: '예정' },
  { value: 'live', label: '진행' },
  { value: 'finished', label: '종료' },
];

const defaultSorts: Record<SlateStatusFilter, SlateSort> = {
  all: 'recommended',
  scheduled: 'startTime',
  live: 'recommended',
  finished: 'recommended',
};

// 날짜 네비게이터는 과거 슬레이트 탐색이 의미 있는 전체·종료 탭에서만 노출한다.
// 예정은 현재 이후 전체 경기, 진행은 오늘 슬레이트를 조회하므로 날짜 선택이 필요 없다.
const DATE_NAVIGABLE: SlateStatusFilter[] = ['all', 'finished'];
const HOME_DATE_STORAGE_KEY = 'pulse.home.slate-date';

// 빈 목록 안내는 탭 성격에 맞춘다(종료 탭인데 "예정" 안내가 뜨는 오해 방지).
const emptyMessages: Record<SlateStatusFilter, string> = {
  all: '이 날짜에는 경기가 없어요.',
  scheduled: '예정된 경기가 없어요.',
  live: '지금 진행 중인 경기가 없어요.',
  finished: '이 날짜에는 종료된 경기가 없어요.',
};

function getStoredHomeDate(): string | undefined {
  try {
    return sessionStorage.getItem(HOME_DATE_STORAGE_KEY) ?? undefined;
  } catch {
    return undefined;
  }
}

function storeHomeDate(date: string) {
  try {
    sessionStorage.setItem(HOME_DATE_STORAGE_KEY, date);
  } catch {
    // 저장소를 사용할 수 없는 환경에서도 URL 기반 날짜 이동은 유지한다.
  }
}

function HomePage() {
  // SSE 신호 수신 → 랭킹·목록 재조회 (홈이 열려 있는 동안 구독)
  useSse();

  const [searchParams, setSearchParams] = useSearchParams();
  const date = searchParams.get('date') ?? getStoredHomeDate();
  const [status, setStatus] = useState<SlateStatusFilter>('all');
  const [sorts, setSorts] = useState(defaultSorts);

  const changeDate = (nextDate: string) => {
    storeHomeDate(nextDate);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.set('date', nextDate);
      return next;
    });
  };

  const sort = sorts[status];

  const changeSort = (nextSort: SlateSort) => {
    setSorts((current) => ({ ...current, [status]: nextSort }));
  };

  const showDateNavigator = DATE_NAVIGABLE.includes(status);
  const today = todaySlateDate();
  // 예정 탭은 현재 이후 전체 경기, 진행 탭은 오늘 슬레이트를 조회한다.
  const effectiveDate = showDateNavigator ? date : undefined;

  const rankingsQuery = useLiveRankings();
  const teamsQuery = useTeamCatalog();
  const gamesQuery = useGames({ date: effectiveDate, status, sort });

  const recommendedCards = rankingsQuery.data
    ? toRecommendedCards(rankingsQuery.data, teamsQuery.data)
    : undefined;

  const slateDate = gamesQuery.data?.slateDate ?? effectiveDate;
  const games = gamesQuery.data?.games ?? [];

  const showRecommended =
    rankingsQuery.isLoading || (recommendedCards && recommendedCards.length > 0);

  return (
    <div className="mx-auto max-w-[1120px] px-4 py-6 sm:py-8">
      {/* 상단 추천: 추천이 하나도 없으면 영역 자체를 숨긴다 */}
      {showRecommended && (
        <section className="mb-8 sm:mb-10">
          <SectionHeader title="지금 볼 만한 경기" />
          <RecommendedGrid
            cards={recommendedCards}
            isLoading={rankingsQuery.isLoading}
          />
        </section>
      )}

      <section>
        <SectionHeader title="전체 경기" />

        <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          {showDateNavigator ? (
            <DateNavigator
              slateDate={slateDate}
              maxDate={today}
              onChange={changeDate}
            />
          ) : (
            <div className="hidden lg:block" />
          )}

          <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center lg:w-auto">
            <SegmentToggle
              options={statusOptions}
              value={status}
              onChange={setStatus}
              ariaLabel="경기 상태 필터"
              className="grid w-full grid-cols-4 sm:inline-flex sm:w-auto"
            />

            <select
              value={sort}
              onChange={(event) => changeSort(event.target.value as SlateSort)}
              aria-label="정렬"
              className="w-full rounded-[9px] border border-card-border bg-white px-3 py-2 text-sm font-medium text-text-body sm:w-auto"
            >
              <option value="recommended">추천순</option>
              <option value="startTime">날짜순</option>
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
          <EmptyState message={emptyMessages[status]} />
        ) : (
          <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {games.map((game) => (
              <li key={game.gameId} className="grid">
                <GameCard
                  game={toSlateCard(game, teamsQuery.data)}
                  variant="tile"
                  showTeamLogos
                />
              </li>
            ))}
          </ul>
        )}

      </section>
    </div>
  );
}

export default HomePage;
