import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { queryKeys } from '../lib/queryKeys';

import { useSse } from './useSse';

type EventSourceListener = (event: Event) => void;

class EventSourceDouble {
  static instances: EventSourceDouble[] = [];

  readonly url: string;
  readonly close = vi.fn();
  private readonly listeners = new Map<string, EventSourceListener[]>();

  constructor(url: string | URL) {
    this.url = String(url);
    EventSourceDouble.instances.push(this);
  }

  addEventListener(type: string, listener: EventSourceListener) {
    const listeners = this.listeners.get(type) ?? [];
    listeners.push(listener);
    this.listeners.set(type, listeners);
  }

  emit(type: string, data?: string) {
    const event = data === undefined
      ? new Event(type)
      : new MessageEvent(type, { data });
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }
}

function renderUseSse() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries')
    .mockResolvedValue();
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return {
    ...renderHook(() => useSse(), { wrapper }),
    invalidateQueries,
  };
}

beforeEach(() => {
  vi.useFakeTimers();
  EventSourceDouble.instances = [];
  vi.stubGlobal('EventSource', EventSourceDouble);
});

afterEach(() => {
  cleanup();
  vi.runOnlyPendingTimers();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('useSse', () => {
  it('마운트하면 SSE 엔드포인트에 연결한다', () => {
    renderUseSse();

    expect(EventSourceDouble.instances).toHaveLength(1);
    expect(EventSourceDouble.instances[0]?.url).toBe('/api/sse');
  });

  it('순위 변경 이벤트를 병합해 순위와 경기 목록을 한 번씩 갱신한다', () => {
    const { invalidateQueries } = renderUseSse();
    const source = EventSourceDouble.instances[0];

    act(() => {
      source?.emit('ranking_changed');
      source?.emit('ranking_changed');
      vi.advanceTimersByTime(3_000);
    });

    expect(invalidateQueries).toHaveBeenCalledTimes(2);
    expect(invalidateQueries).toHaveBeenNthCalledWith(1, {
      queryKey: queryKeys.rankings.live,
    });
    expect(invalidateQueries).toHaveBeenNthCalledWith(2, {
      queryKey: [...queryKeys.games.all, 'list'],
    });
  });

  it('여러 경기 변경과 잘못된 이벤트를 구분해 정상 경기만 갱신한다', () => {
    const { invalidateQueries } = renderUseSse();
    const source = EventSourceDouble.instances[0];

    act(() => {
      source?.emit('game_updated', JSON.stringify({ gameId: 101 }));
      source?.emit('game_updated', '잘못된 JSON');
      source?.emit('game_updated', JSON.stringify({ gameId: 202 }));
      vi.advanceTimersByTime(3_000);
    });

    expect(invalidateQueries.mock.calls.map(([filters]) => filters?.queryKey))
      .toEqual([
        [...queryKeys.games.all, 'detail', '101'],
        [...queryKeys.games.all, 'events', '101'],
        [...queryKeys.games.all, 'detail', '202'],
        [...queryKeys.games.all, 'events', '202'],
      ]);
  });

  it('오류 후 브라우저 재연결을 막지 않고 언마운트할 때 연결을 닫는다', () => {
    const { unmount } = renderUseSse();
    const source = EventSourceDouble.instances[0];

    act(() => source?.emit('error'));
    expect(source?.close).not.toHaveBeenCalled();

    unmount();
    expect(source?.close).toHaveBeenCalledTimes(1);
  });

  it('언마운트하면 대기 중인 갱신 타이머를 취소한다', () => {
    const { invalidateQueries, unmount } = renderUseSse();
    const source = EventSourceDouble.instances[0];

    act(() => source?.emit('ranking_changed'));
    unmount();
    act(() => vi.advanceTimersByTime(3_000));

    expect(source?.close).toHaveBeenCalledTimes(1);
    expect(invalidateQueries).not.toHaveBeenCalled();
  });
});
