import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import {
  TOAST_EVENT,
  type ToastPayload,
} from './toastEvent';

/**
 * 화면에 실제로 표시되는 토스트 한 건입니다.
 *
 * ToastPayload에 화면 렌더링용 고유 ID를 추가합니다.
 */
interface ToastEntry extends ToastPayload {
  id: number;
}

/** 토스트 한 건이 화면에 유지되는 시간 */
const AUTO_DISMISS_MS = 5000;

/**
 * 한꺼번에 화면에 보여줄 최대 토스트 개수입니다.
 *
 * 알림이 연달아 발생하더라도 화면을 너무 많이 가리지 않도록
 * 최근 알림 세 건까지만 표시합니다.
 */
const MAX_VISIBLE_TOASTS = 3;

/** 브라우저에서 토스트마다 부여할 임시 고유 ID */
let nextId = 1;

/**
 * 애플리케이션 전역에서 사용하는 실시간 알림 토스트입니다.
 *
 * 주요 동작:
 * - 화면 위쪽에서 모바일 알림 형태로 표시
 * - 최신 알림을 가장 위에 배치
 * - 서로 다른 알림은 최대 세 건까지 쌓아서 표시
 * - 같은 dedupeKey를 가진 알림은 중복 표시하지 않음
 * - 다섯 초 후 자동으로 사라짐
 * - 클릭 시 연결된 경기 상세 페이지로 이동
 */
export function ToastHost() {
  const navigate = useNavigate();

  const [toasts, setToasts] = useState<ToastEntry[]>([]);

  useEffect(() => {
    /**
     * showToast()가 발생시킨 전역 이벤트를 처리합니다.
     */
    const onToast = (event: Event) => {
      const { detail } =
        event as CustomEvent<ToastPayload>;

      const entry: ToastEntry = {
        ...detail,
        id: nextId++,
      };

      setToasts((previousToasts) => {
        /**
         * 같은 알림 ID로 만든 토스트가 이미 화면에 있다면
         * 동일한 토스트를 다시 추가하지 않습니다.
         */
        const isDuplicated =
          entry.dedupeKey !== undefined &&
          previousToasts.some(
            (toast) =>
              toast.dedupeKey === entry.dedupeKey,
          );

        if (isDuplicated) {
          return previousToasts;
        }

        /**
         * 새 알림을 배열 앞쪽에 추가합니다.
         *
         * 따라서 가장 최근에 도착한 알림이 위에 표시되고,
         * 기존 알림은 아래쪽으로 밀립니다.
         */
        return [
          entry,
          ...previousToasts,
        ].slice(0, MAX_VISIBLE_TOASTS);
      });

      /**
       * 지정된 시간이 지나면 해당 토스트만 제거합니다.
       */
      window.setTimeout(() => {
        setToasts((previousToasts) =>
          previousToasts.filter(
            (toast) => toast.id !== entry.id,
          ),
        );
      }, AUTO_DISMISS_MS);
    };

    window.addEventListener(TOAST_EVENT, onToast);

    return () => {
      window.removeEventListener(
        TOAST_EVENT,
        onToast,
      );
    };
  }, []);

  if (toasts.length === 0) {
    return null;
  }

  return (
    <>
      {/*
       * 별도 CSS 파일을 추가하지 않고도
       * 토스트가 위에서 내려왔다가 자연스럽게 사라지도록 합니다.
       */}
      <style>
        {`
          @keyframes pulse-toast-lifecycle {
            0% {
              opacity: 0;
              transform: translateY(-14px) scale(0.98);
            }

            7% {
              opacity: 1;
              transform: translateY(0) scale(1);
            }

            92% {
              opacity: 1;
              transform: translateY(0) scale(1);
            }

            100% {
              opacity: 0;
              transform: translateY(-8px) scale(0.98);
            }
          }
        `}
      </style>

      <div
        aria-label="실시간 경기 알림"
        aria-live="polite"
        className="
          pointer-events-none
          fixed
          right-3
          top-20
          z-[70]
          flex
          w-[min(420px,calc(100vw-24px))]
          flex-col
          gap-2.5
          sm:right-6
        "
      >
        {toasts.map((toast) => {
          const isGameStart =
            toast.variant === 'game-start';

          return (
            <button
              key={toast.id}
              type="button"
              onClick={() => {
                if (toast.to) {
                  navigate(toast.to);
                }

                setToasts((previousToasts) =>
                  previousToasts.filter(
                    (currentToast) =>
                      currentToast.id !== toast.id,
                  ),
                );
              }}
              style={{
                animation:
                  `pulse-toast-lifecycle ` +
                  `${AUTO_DISMISS_MS}ms ease-in-out both`,
              }}
              className="
                group
                pointer-events-auto
                relative
                overflow-hidden
                rounded-2xl
                border
                border-white/15
                bg-gradient-to-br
                from-[#0B2559]/95
                to-[#04122E]/95
                px-4
                py-3.5
                text-left
                text-white
                shadow-hero
                backdrop-blur-md
                transition
                hover:-translate-y-0.5
                hover:border-white/25
                focus-visible:outline-none
                focus-visible:ring-2
                focus-visible:ring-white/40
              "
            >
              {/* 알림 종류를 구분하는 왼쪽 강조선 */}
              <span
                aria-hidden="true"
                className={`
                  absolute
                  inset-y-0
                  left-0
                  w-1
                  ${isGameStart
                    ? 'bg-emerald-500'
                    : 'bg-mlb-red'
                  }
                `}
              />

              <span className="flex items-start gap-3">
                {/* 알림 종류 아이콘 */}
                <span
                  aria-hidden="true"
                  className="
                    mt-0.5
                    flex
                    h-9
                    w-9
                    shrink-0
                    items-center
                    justify-center
                    rounded-full
                    border
                    border-white/10
                    bg-white/10
                    text-base
                  "
                >
                  {isGameStart ? '⚾' : '⚡'}
                </span>

                <span className="min-w-0 flex-1">
                  {/* 알림 제목 */}
                  <span className="block text-sm font-bold leading-5">
                    {toast.title}
                  </span>

                  {/* 서버가 완성한 실제 알림 메시지 */}
                  <span className="mt-0.5 block break-words text-sm leading-5 text-white/75">
                    {toast.message}
                  </span>

                  {/* 이동할 경로가 있는 경우에만 안내 표시 */}
                  {toast.to && (
                    <span
                      className="
                        mt-2
                        inline-flex
                        items-center
                        gap-1
                        text-xs
                        font-semibold
                        text-white/85
                        transition-colors
                        group-hover:text-white
                      "
                    >
                      경기 보기
                      <span aria-hidden="true">›</span>
                    </span>
                  )}
                </span>
              </span>
            </button>
          );
        })}
      </div>
    </>
  );
}