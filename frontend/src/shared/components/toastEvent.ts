/**
 * 토스트에 적용할 알림 종류입니다.
 *
 * game-start:
 * 사용자가 관심 팀으로 등록한 팀의 경기 시작 알림
 *
 * surge:
 * 지금 볼 만한 경기 흐름이 감지됐을 때 표시하는 알림
 */
export type ToastVariant = 'game-start' | 'surge';

export interface ToastPayload {
  /**
   * 토스트 위쪽에 표시할 짧은 제목입니다.
   *
   * 예:
   * - 관심 팀 경기가 시작됐어요
   * - 지금 볼 만한 경기 알림
   */
  title: string;

  /**
   * 서버가 완성해서 내려준 실제 알림 메시지입니다.
   *
   * 프론트에서 경기 상황 문구를 다시 조립하지 않고
   * 알림 API의 message를 그대로 전달합니다.
   */
  message: string;

  /** 클릭했을 때 이동할 경로입니다. */
  to?: string;

  /** 경기 시작과 경기 흐름 알림의 UI를 구분합니다. */
  variant: ToastVariant;

  /**
   * 동일한 알림을 토스트 목록에 중복으로 추가하지 않기 위한 키입니다.
   *
   * 알림 테이블의 notificationId를 사용하는 것이 가장 안전합니다.
   */
  dedupeKey?: string;
}

export const TOAST_EVENT = 'pulse-toast';

/** 서버 알림 정보를 전역 ToastHost에 전달합니다. */
export function showToast(payload: ToastPayload) {
  window.dispatchEvent(
    new CustomEvent<ToastPayload>(TOAST_EVENT, {
      detail: payload,
    }),
  );
}