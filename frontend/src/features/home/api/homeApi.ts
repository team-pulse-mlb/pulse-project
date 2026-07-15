import apiClient from '../../../shared/api/httpClient';

import type {
  HomeRankingResponse,
  HomeSlateResponse,
  SlateSort,
  SlateStatusFilter,
} from './types';

/** 홈 상단 추천 (최대 5장, 진행→종료→예정 슬롯) */
export async function fetchLiveRankings(): Promise<HomeRankingResponse> {
  const response = await apiClient.get<HomeRankingResponse>('/api/rankings/live');
  return response.data;
}

interface FetchGamesParams {
  /** 미지정 시 오늘 슬레이트. 예정 상태에서는 날짜와 무관하게 현재 이후 전체 경기를 조회한다. */
  date?: string;
  status: SlateStatusFilter;
  sort: SlateSort;
}

/** 홈 하단 전체 경기 목록 (예정은 현재 이후 전체, 나머지는 슬레이트 단위) */
export async function fetchGames(
  params: FetchGamesParams,
): Promise<HomeSlateResponse> {
  const response = await apiClient.get<HomeSlateResponse>('/api/games', {
    params,
  });
  return response.data;
}
