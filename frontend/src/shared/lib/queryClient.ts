import { QueryClient } from '@tanstack/react-query';

// 서버 상태 캐시 전역 인스턴스.
// 실시간 갱신은 SSE 신호 수신 → invalidateQueries 재조회로 처리하므로
// 창 포커스 재조회는 끄고, staleTime은 짧게 유지한다.
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      staleTime: 10_000,
      retry: 1,
    },
  },
});
