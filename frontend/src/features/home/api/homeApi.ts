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
  /** 미지정 시 오늘 슬레이트(미 동부시간) */
  date?: string;
  status: SlateStatusFilter;
  sort: SlateSort;
}

/** 홈 하단 전체 경기 목록 (슬레이트 단위) */
export async function fetchGames(
  params: FetchGamesParams,
): Promise<HomeSlateResponse> {
  const response = await apiClient.get<HomeSlateResponse>('/api/games', {
    params,
  });
  return response.data;
}
