import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { TOAST_EVENT, type ToastPayload } from './toastEvent';

// 토스트: 서버가 완성한 message 문자열을 그대로 표시한다 (프론트 문구 조립 금지).
// 사용처는 showToast()만 호출하면 되고, ToastHost는 MainLayout에 한 번만 둔다.

interface ToastEntry extends ToastPayload {
  id: number;
}

const AUTO_DISMISS_MS = 5000;

let nextId = 1;

export function ToastHost() {
  const navigate = useNavigate();
  const [toasts, setToasts] = useState<ToastEntry[]>([]);

  useEffect(() => {
    const onToast = (event: Event) => {
      const { detail } = event as CustomEvent<ToastPayload>;
      const entry: ToastEntry = { ...detail, id: nextId++ };

      setToasts((prev) => [...prev, entry]);

      window.setTimeout(() => {
        setToasts((prev) => prev.filter((t) => t.id !== entry.id));
      }, AUTO_DISMISS_MS);
    };

    window.addEventListener(TOAST_EVENT, onToast);
    return () => window.removeEventListener(TOAST_EVENT, onToast);
  }, []);

  if (toasts.length === 0) {
    return null;
  }

  return (
    <div className="fixed bottom-6 left-1/2 z-50 flex w-[min(420px,calc(100vw-32px))] -translate-x-1/2 flex-col gap-2">
      {toasts.map((toast) => (
        <button
          key={toast.id}
          type="button"
          onClick={() => {
            if (toast.to) {
              navigate(toast.to);
            }
            setToasts((prev) => prev.filter((t) => t.id !== toast.id));
          }}
          className="rounded-panel border border-white/10 bg-ink px-5 py-3.5 text-left text-sm text-white shadow-modal transition-transform hover:-translate-y-0.5"
        >
          {toast.message}
        </button>
      ))}
    </div>
  );
}
