import { useQuery } from '@tanstack/react-query';

import { getTeams } from '../../../shared/api/teamApi';
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

/** 홈 경기 카드에서 사용할 팀 약어와 로고 목록 */
export function useTeamCatalog() {
  return useQuery({
    queryKey: queryKeys.teams,
    queryFn: getTeams,
    staleTime: 24 * 60 * 60 * 1000,
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
