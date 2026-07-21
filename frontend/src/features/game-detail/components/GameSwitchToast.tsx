import {
    useCallback,
    useEffect,
    useRef,
    useState,
} from 'react';
import { useNavigate } from 'react-router';

import type {
    GameSwitchSuggestionViewModel,
} from '../model/gameDetailViewModels';

const AUTO_DISMISS_MS = 10_000;
const EXIT_ANIMATION_MS = 180;
const COOLDOWN_MS = 15 * 60 * 1_000;
const STORAGE_PREFIX =
    'pulse:game-switch-suggestion:';

interface GameSwitchToastProps {
    currentGameId: number;
    suggestion:
        GameSwitchSuggestionViewModel
        | null;
}

function storageKey(
    gameId: number,
): string {
    return `${STORAGE_PREFIX}${gameId}`;
}

function wasRecentlyShown(
    gameId: number,
): boolean {
    try {
        const storedValue =
            localStorage.getItem(
                storageKey(gameId),
            );

        if (!storedValue) {
            return false;
        }

        const shownAt =
            Number(storedValue);

        return (
            Number.isFinite(shownAt)
            && Date.now() - shownAt
                < COOLDOWN_MS
        );
    } catch {
        return false;
    }
}

function rememberShown(
    gameId: number,
): void {
    try {
        localStorage.setItem(
            storageKey(gameId),
            String(Date.now()),
        );
    } catch {
        /*
         * 저장소를 사용할 수 없는 환경에서도
         * 전환 토스트 표시는 계속한다.
         */
    }
}

/**
 * 경기 상세 응답의 switchSuggestion만 표시하는
 * 일회성 경기 전환 토스트다.
 *
 * 저장 알림 파이프라인을 사용하지 않으므로
 * 알림센터에는 남지 않는다.
 */
function GameSwitchToast({
    currentGameId,
    suggestion,
}: GameSwitchToastProps) {
    const navigate = useNavigate();

    const activeSuggestionId =
        useRef<number | null>(null);

    const exitTimeoutId =
        useRef<number | null>(null);

    const [
        visibleSuggestionId,
        setVisibleSuggestionId,
    ] = useState<number | null>(null);

    const [
        countdownStarted,
        setCountdownStarted,
    ] = useState(false);

    const [
        toastEntered,
        setToastEntered,
    ] = useState(false);

    const suggestionGameId =
        suggestion?.gameId ?? null;

    const beginDismiss = useCallback(
        (afterExit?: () => void) => {
            if (
                exitTimeoutId.current
                !== null
            ) {
                return;
            }

            setToastEntered(false);

            exitTimeoutId.current =
                window.setTimeout(
                    () => {
                        setVisibleSuggestionId(
                            null,
                        );

                        setCountdownStarted(
                            false,
                        );

                        exitTimeoutId.current =
                            null;

                        afterExit?.();
                    },
                    EXIT_ANIMATION_MS,
                );
        },
        [],
    );

    useEffect(() => {
        let showFrameId:
            number | null = null;

        let enterFrameId:
            number | null = null;

        let hideFrameId:
            number | null = null;

        let timeoutId:
            number | null = null;

        if (
            suggestionGameId === null
            || suggestionGameId
                === currentGameId
        ) {
            hideFrameId =
                window.requestAnimationFrame(
                    () => {
                        activeSuggestionId.current =
                            null;

                        setToastEntered(false);

                        setVisibleSuggestionId(
                            null,
                        );

                        setCountdownStarted(
                            false,
                        );
                    },
                );

            return () => {
                if (hideFrameId !== null) {
                    window.cancelAnimationFrame(
                        hideFrameId,
                    );
                }
            };
        }

        const isCurrentSuggestion =
            activeSuggestionId.current
            === suggestionGameId;

        if (
            !isCurrentSuggestion
            && wasRecentlyShown(
                suggestionGameId,
            )
        ) {
            hideFrameId =
                window.requestAnimationFrame(
                    () => {
                        setToastEntered(false);

                        setVisibleSuggestionId(
                            null,
                        );

                        setCountdownStarted(
                            false,
                        );
                    },
                );

            return () => {
                if (hideFrameId !== null) {
                    window.cancelAnimationFrame(
                        hideFrameId,
                    );
                }
            };
        }

        if (!isCurrentSuggestion) {
            rememberShown(
                suggestionGameId,
            );

            activeSuggestionId.current =
                suggestionGameId;
        }

        showFrameId =
            window.requestAnimationFrame(
                () => {
                    setVisibleSuggestionId(
                        suggestionGameId,
                    );

                    setToastEntered(false);

                    setCountdownStarted(
                        false,
                    );

                    enterFrameId =
                        window.requestAnimationFrame(
                            () => {
                                setToastEntered(
                                    true,
                                );

                                setCountdownStarted(
                                    true,
                                );
                            },
                        );
                },
            );

        timeoutId =
            window.setTimeout(
                () => {
                    beginDismiss();
                },
                AUTO_DISMISS_MS,
            );

        return () => {
            if (showFrameId !== null) {
                window.cancelAnimationFrame(
                    showFrameId,
                );
            }

            if (enterFrameId !== null) {
                window.cancelAnimationFrame(
                    enterFrameId,
                );
            }

            if (timeoutId !== null) {
                window.clearTimeout(
                    timeoutId,
                );
            }

            if (
                exitTimeoutId.current
                !== null
            ) {
                window.clearTimeout(
                    exitTimeoutId.current,
                );

                exitTimeoutId.current =
                    null;
            }
        };
    }, [
        beginDismiss,
        currentGameId,
        suggestionGameId,
    ]);

    if (
        suggestion === null
        || visibleSuggestionId
            !== suggestion.gameId
    ) {
        return null;
    }

    return (
        <div
            className="
                fixed bottom-6 left-1/2 z-50
                w-[min(420px,calc(100vw-32px))]
                -translate-x-1/2
            "
            role="status"
            aria-live="polite"
        >
            <div
                className={`
                    relative overflow-hidden
                    rounded-panel
                    border border-white/15
                    bg-mlb-navy text-white
                    shadow-modal
                    transition-[transform,opacity]
                    will-change-transform
                    ${
                        toastEntered
                            ? 'translate-y-0 scale-100 opacity-100 duration-[220ms] ease-[cubic-bezier(0.16,1,0.3,1)]'
                            : 'pointer-events-none translate-y-3 scale-[0.98] opacity-0 duration-[180ms] ease-in'
                    }
                `}
            >
                <button
                    type="button"
                    onClick={() => {
                        beginDismiss(
                            () => {
                                navigate(
                                    `/games/${suggestion.gameId}`,
                                );
                            },
                        );
                    }}
                    className="
                        w-full px-5 pb-5
                        pl-5 pr-14 pt-4
                        text-left
                        transition-colors
                        hover:bg-white/10
                    "
                >
                    <span className="flex items-start gap-3">
                        <span
                            aria-hidden="true"
                            className="
                                mt-0.5 flex size-7
                                shrink-0 items-center
                                justify-center rounded-full
                                border border-white/70
                                text-base font-semibold
                            "
                        >
                            !
                        </span>

                        <span className="min-w-0">
                            <span
                                className="
                                    block text-base
                                    font-semibold
                                "
                            >
                                경기 추천 알림
                            </span>

                            <span
                                className="
                                    mt-1 block text-sm
                                    leading-5 text-white/90
                                "
                            >
                                {suggestion.message}
                            </span>
                        </span>
                    </span>
                </button>

                <button
                    type="button"
                    aria-label="경기 전환 알림 닫기"
                    onClick={() => {
                        beginDismiss();
                    }}
                    className="
                        absolute right-3 top-3
                        z-10 flex size-8
                        items-center justify-center
                        rounded-full
                        text-xl leading-none
                        text-white/80
                        transition-colors
                        hover:bg-white/15
                        hover:text-white
                    "
                >
                    <span aria-hidden="true">
                        ×
                    </span>
                </button>

                <span
                    aria-hidden="true"
                    className="
                        absolute inset-x-0
                        bottom-0 h-1
                        bg-white/25
                    "
                >
                    <span
                        className="
                            block h-full w-full
                            origin-left bg-white/80
                        "
                        style={{
                            transform:
                                countdownStarted
                                    ? 'scaleX(0)'
                                    : 'scaleX(1)',

                            transition:
                                `transform ${AUTO_DISMISS_MS}ms linear`,
                        }}
                    />
                </span>
            </div>
        </div>
    );
}

export default GameSwitchToast;
