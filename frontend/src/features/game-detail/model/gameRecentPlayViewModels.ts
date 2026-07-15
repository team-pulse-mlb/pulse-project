/**
 * 최근 플레이 목록이 화면에 표시하는 데이터다.
 *
 * API 응답 타입과 UI 타입을 분리해서
 * 백엔드 응답 구조가 화면 컴포넌트에 직접 의존하지 않게 한다.
 */
export interface RecentPlayViewModel {
    playId: number;
    inning: number;
    inningType: 'TOP' | 'BOTTOM';
    text: string;
    scoreLabel: string;
    highlighted: boolean;
}