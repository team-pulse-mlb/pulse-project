import { useQuery } from '@tanstack/react-query';

import { queryKeys } from '../../../shared/lib/queryKeys';
import { fetchGames, fetchLiveRankings } from '../api/homeApi';
import type { SlateSort, SlateStatusFilter } from '../api/types';

/** 홈 상단 추천 랭킹. SSE ranking_changed 수신 시 useSse가 invalidate한다. */
export function useLiveRankings() {
  return useQuery({
    queryKey: queryKeys.rankings.live,
    queryFn: fetchLiveRankings,
  });
}

interface UseGamesParams {
  date?: string;
  status: SlateStatusFilter;
  sort: SlateSort;
}

/** 홈 하단 슬레이트 목록 */
export function useGames(params: UseGamesParams) {
  return useQuery({
    queryKey: queryKeys.games.list(params),
    queryFn: () => fetchGames(params),
  });
}
