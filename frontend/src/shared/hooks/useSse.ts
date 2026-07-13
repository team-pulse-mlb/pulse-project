import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import ApiUrl from '../api/ApiUrl';
import { queryKeys } from '../lib/queryKeys';

// SSE 구독 훅. 이벤트 payload에는 데이터가 없고 "신호 + 재조회" 방식이다.
// 비로그인 연결(GET /api/sse)은 ranking_changed·game_updated만 수신한다.
// 알림(notification_created)은 인증 토큰 연결이 필요하며 features/notification 담당(윤호) 영역.
export function useSse() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const source = new EventSource(`${ApiUrl}/api/sse`);

    source.addEventListener('ranking_changed', () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.rankings.live });
      queryClient.invalidateQueries({ queryKey: [...queryKeys.games.all, 'list'] });
    });

    source.addEventListener('game_updated', (event) => {
      try {
        const { gameId } = JSON.parse((event as MessageEvent).data) as {
          gameId: number;
        };

        // 현재 mode를 유지한 채 해당 경기의 상세·이벤트만 재조회한다.
        queryClient.invalidateQueries({
          queryKey: [...queryKeys.games.all, 'detail', String(gameId)],
        });
        queryClient.invalidateQueries({
          queryKey: [...queryKeys.games.all, 'events', String(gameId)],
        });
      } catch {
        // payload 파싱 실패 시 무시 (다음 신호에서 복구)
      }
    });

    // 연결 오류 시 EventSource가 자체 재연결한다. 재연결 후 첫 신호로 최신화된다.
    return () => source.close();
  }, [queryClient]);
}
