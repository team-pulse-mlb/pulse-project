import apiClient from './httpClient';

/**
 * 현재 백엔드가 제공하는 알림 종류입니다.
 *
 * GAME_START:
 * 사용자가 관심 팀으로 설정한 팀의 경기 시작 알림
 *
 * SURGE:
 * 경기의 중요도 또는 모멘텀이 급상승했을 때 발생하는 알림
 */
export type NotificationType = 'GAME_START' | 'SURGE';

/**
 * GET /api/me/notifications 응답 한 건의 구조입니다.
 */
export interface NotificationSummary {
  /** user_notifications 테이블의 PK */
  notificationId: number;

  /** 알림 종류 */
  type: NotificationType;

  /** 알림이 발생한 경기 ID */
  gameId: number;

  /**
   * 서버가 완성해서 내려준 알림 문구입니다.
   *
   * 스포일러 정책 등의 이유로 프론트에서 문구를 조립하지 않고
   * 서버 응답을 그대로 표시합니다.
   */
  message: string;

  /** 읽음 여부 */
  read: boolean;

  /** 읽은 시간. 아직 읽지 않았다면 null */
  readAt: string | null;

  /** 알림 생성 시간 */
  createdAt: string;
}

/**
 * 알림 읽음 처리 요청 구조입니다.
 */
interface NotificationReadRequest {
  /**
   * true이면 모든 미읽음 알림 처리,
   * false이면 notificationIds에 포함된 알림만 처리합니다.
   */
  all: boolean;

  /** 선택 읽음 처리할 알림 ID 목록 */
  notificationIds?: number[];
}

/**
 * 알림 읽음 처리 응답 구조입니다.
 */
export interface NotificationReadResponse {
  /** 실제 읽음 처리된 알림 개수 */
  updatedCount: number;
}

/**
 * 현재 로그인 사용자의 최근 7일 알림을 최신순으로 조회합니다.
 */
export async function fetchMyNotifications(): Promise<
  NotificationSummary[]
> {
  const response = await apiClient.get<NotificationSummary[]>(
    '/api/me/notifications',
  );

  return response.data;
}

/**
 * 알림 읽음 처리 API를 호출하는 내부 공통 함수입니다.
 */
async function markNotificationsAsRead(
  request: NotificationReadRequest,
): Promise<NotificationReadResponse> {
  const response = await apiClient.post<NotificationReadResponse>(
    '/api/me/notifications/read',
    request,
  );

  return response.data;
}

/**
 * 선택한 알림만 읽음 처리합니다.
 */
export async function markSelectedNotificationsAsRead(
  notificationIds: number[],
): Promise<NotificationReadResponse> {
  return markNotificationsAsRead({
    all: false,
    notificationIds,
  });
}

/**
 * 현재 사용자의 모든 미읽음 알림을 읽음 처리합니다.
 */
export async function markAllNotificationsAsRead(): Promise<
  NotificationReadResponse
> {
  return markNotificationsAsRead({
    all: true,
  });
}


/**
 * POST /api/sse/token 응답 구조입니다.
 *
 * token은 Redis에 60초 동안 저장되며,
 * GET /api/sse?token=... 연결에서 한 번 사용하면 삭제됩니다.
 */
export interface SseTokenResponse {
  token: string;
}

/**
 * 로그인 사용자의 SSE 인증 연결에 사용할
 * 일회용 토큰을 발급합니다.
 *
 * apiClient를 사용하므로 Authorization 헤더가 자동으로 붙고,
 * Access Token이 만료됐다면 기존 인터셉터가 재발급을 시도합니다.
 */
export async function issueSseToken(): Promise<string> {
  const response = await apiClient.post<SseTokenResponse>(
    '/api/sse/token',
  );

  return response.data.token;
}