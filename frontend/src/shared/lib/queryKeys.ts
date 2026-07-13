// 쿼리 키 중앙 정의.
// SSE 수신부(useSse)와 각 feature 훅이 같은 키를 참조하도록 여기서만 만든다.
export const queryKeys = {
  rankings: {
    live: ['rankings', 'live'] as const,
  },
  games: {
    all: ['games'] as const,
    list: (params: { date?: string; status?: string; sort?: string }) =>
      ['games', 'list', params] as const,
    detail: (gameId: string, mode: string) =>
      ['games', 'detail', gameId, mode] as const,
    events: (gameId: string, mode: string) =>
      ['games', 'events', gameId, mode] as const,
  },
  me: {
    preferences: ['me', 'preferences'] as const,
    notifications: ['me', 'notifications'] as const,
  },
  teams: ['teams'] as const,
} as const;
