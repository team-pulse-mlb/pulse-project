import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import {
    MemoryRouter,
    useLocation,
} from 'react-router';
import {
    afterEach,
    beforeEach,
    describe,
    expect,
    it,
    vi,
} from 'vitest';

import type {
    GameSwitchSuggestionViewModel,
} from '../model/gameDetailViewModels';

import GameSwitchToast from './GameSwitchToast';

const suggestion: GameSwitchSuggestionViewModel = {
    gameId: 202,
    matchup: {
        away: 'Detroit Tigers',
        home: 'Texas Rangers',
    },
    latestTag: '득점권 압박',
    message:
        '지금은 다른 경기가 더 볼 만해요. <득점권 압박>',
};

function LocationProbe() {
    const location = useLocation();

    return (
        <span data-testid="location">
            {location.pathname}
        </span>
    );
}

function renderToast() {
    return render(
        <MemoryRouter
            initialEntries={['/games/101']}
        >
            <GameSwitchToast
                currentGameId={101}
                suggestion={suggestion}
            />

            <LocationProbe />
        </MemoryRouter>,
    );
}

function advanceFrame() {
    act(() => {
        vi.advanceTimersByTime(1);
    });

    act(() => {
        vi.advanceTimersByTime(1);
    });
}

beforeEach(() => {
    vi.useFakeTimers();
    localStorage.clear();

    vi.stubGlobal(
        'requestAnimationFrame',
        (
            callback:
                FrameRequestCallback,
        ) => window.setTimeout(
            () => callback(
                performance.now(),
            ),
            0,
        ),
    );

    vi.stubGlobal(
        'cancelAnimationFrame',
        (id: number) => {
            window.clearTimeout(id);
        },
    );
});

afterEach(() => {
    cleanup();
    vi.clearAllTimers();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    localStorage.clear();
});

describe('GameSwitchToast', () => {
    it('서버가 만든 전환 안내 문구를 표시한다', () => {
        renderToast();
        advanceFrame();

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).not.toBeNull();

        expect(
            screen.queryByText(
                suggestion.message,
            ),
        ).not.toBeNull();
    });

    it('전체 토스트를 누르면 추천 경기로 이동한다', () => {
        renderToast();
        advanceFrame();

        fireEvent.click(
            screen.getByRole(
                'button',
                {
                    name:
                        /지금은 다른 경기가 더 볼 만해요/,
                },
            ),
        );

        act(() => {
            vi.advanceTimersByTime(180);
        });

        expect(
            screen.getByTestId(
                'location',
            ).textContent,
        ).toBe('/games/202');
    });

    it('표시 후 10초가 지나면 자동으로 사라진다', () => {
        renderToast();
        advanceFrame();

        act(() => {
            vi.advanceTimersByTime(
                9_998,
            );
        });

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).not.toBeNull();

        act(() => {
            vi.advanceTimersByTime(1);
        });

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).not.toBeNull();

        act(() => {
            vi.advanceTimersByTime(180);
        });

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).toBeNull();
    });

    it('닫기 버튼을 누르면 경기 이동 없이 사라진다', () => {
        renderToast();
        advanceFrame();

        fireEvent.click(
            screen.getByRole(
                'button',
                {
                    name:
                        '경기 전환 알림 닫기',
                },
            ),
        );

        act(() => {
            vi.advanceTimersByTime(180);
        });

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).toBeNull();

        expect(
            screen.getByTestId(
                'location',
            ).textContent,
        ).toBe('/games/101');
    });

    it('같은 추천 경기는 15분 안에 다시 표시하지 않는다', () => {
        const firstRender =
            renderToast();

        advanceFrame();

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).not.toBeNull();

        firstRender.unmount();

        renderToast();
        advanceFrame();

        expect(
            screen.queryByText(
                '경기 추천 알림',
            ),
        ).toBeNull();
    });
});
