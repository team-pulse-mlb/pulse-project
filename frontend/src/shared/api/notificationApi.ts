import apiClient from './httpClient';

export interface NotificationSummary {
  notificationId: number;
  type: 'GAME_START' | 'SURGE';
  gameId: number;
  message: string;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

/** 로그인 사용자의 최근 7일 알림을 조회한다. */
export async function fetchMyNotifications(): Promise<NotificationSummary[]> {
  const response = await apiClient.get<NotificationSummary[]>(
    '/api/me/notifications',
  );
  return response.data;
}
