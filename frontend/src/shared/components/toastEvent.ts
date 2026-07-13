export interface ToastPayload {
  message: string;
  /** 클릭 시 이동할 경로 (예: /games/123) */
  to?: string;
}

export const TOAST_EVENT = 'pulse-toast';

/** 서버가 완성한 토스트 문구를 전역 호스트에 전달한다. */
export function showToast(payload: ToastPayload) {
  window.dispatchEvent(new CustomEvent<ToastPayload>(TOAST_EVENT, { detail: payload }));
}
