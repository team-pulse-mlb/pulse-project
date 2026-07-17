import { useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import ApiUrl from '../api/ApiUrl';
import { queryKeys } from '../lib/queryKeys';

const INVALIDATE_COALESCE_MS = 3_000;

// 공개 SSE는 랭킹과 경기 변경 신호만 수신하며, 짧은 시간의 연속 신호를 한 번으로 합친다.
export function useSse() {
  const queryClient = useQueryClient();

  useEffect(() => {
    const source = new EventSource(`${ApiUrl}/api/sse`);
    let rankingTimer: ReturnType<typeof setTimeout> | undefined;
    let gameTimer: ReturnType<typeof setTimeout> | undefined;
    const pendingGameIds = new Set<number>();

    source.addEventListener('ranking_changed', () => {
      if (rankingTimer !== undefined) {
        return;
      }
      rankingTimer = setTimeout(() => {
        rankingTimer = undefined;
        queryClient.invalidateQueries({ queryKey: queryKeys.rankings.live });
        queryClient.invalidateQueries({
          queryKey: [...queryKeys.games.all, 'list'],
        });
      }, INVALIDATE_COALESCE_MS);
    });

    source.addEventListener('game_updated', (event) => {
      try {
        const { gameId } = JSON.parse((event as MessageEvent).data) as {
          gameId: number;
        };

        pendingGameIds.add(gameId);
        if (gameTimer !== undefined) {
          return;
        }
        gameTimer = setTimeout(() => {
          gameTimer = undefined;
          const gameIds = [...pendingGameIds];
          pendingGameIds.clear();
          gameIds.forEach((pendingGameId) => {
            queryClient.invalidateQueries({
              queryKey: [
                ...queryKeys.games.all,
                'detail',
                String(pendingGameId),
              ],
            });
            queryClient.invalidateQueries({
              queryKey: [
                ...queryKeys.games.all,
                'events',
                String(pendingGameId),
              ],
            });
          });
        }, INVALIDATE_COALESCE_MS);
      } catch {
        // 잘못된 신호는 무시하고 다음 정상 신호에서 복구한다.
      }
    });

    return () => {
      source.close();
      clearTimeout(rankingTimer);
      clearTimeout(gameTimer);
      pendingGameIds.clear();
    };
  }, [queryClient]);
}
