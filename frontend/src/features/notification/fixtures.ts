// 셸 확인용 더미 — 데이터 연결 시 삭제

export interface NotificationFixture {
  id: number;
  gameId: number;
  message: string;
  matchup: string;
  relativeTime: string;
  unread: boolean;
}

export const notificationFixtures: NotificationFixture[] = [
  { id: 1, gameId: 32, message: '지금 볼 만한 경기가 있어요 — 접전 흐름', matchup: 'SD @ CHC', relativeTime: '3분 전', unread: true },
  { id: 2, gameId: 30, message: '관심 팀 경기가 곧 시작돼요', matchup: 'BOS @ NYY', relativeTime: '1시간 전', unread: false },
  { id: 3, gameId: 42, message: '지금 볼 만한 경기가 있어요 — 후반 긴장 구간', matchup: 'HOU @ SEA', relativeTime: '2시간 전', unread: false },
  { id: 4, gameId: 31, message: '관심 팀 경기가 종료됐어요 — 다시보기 준비 완료', matchup: 'SF @ LAD', relativeTime: '어제', unread: false },
  { id: 5, gameId: 52, message: '지금은 다른 경기가 더 볼 만해요 — 만루 승부', matchup: 'ATL @ NYM', relativeTime: '2일 전', unread: false },
];
