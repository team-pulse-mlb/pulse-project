import EmptyState from '../../../shared/components/EmptyState';

// 알림 센터 골격 (USER_FLOW §4.10).
// 최신순 목록, 미읽음 ●/읽음 ○, 클릭 시 읽음 처리 + 상세 이동.
// 구현은 local-docs/CODEX_FRONTEND_PROMPT.md 위임 명세를 따른다. 데이터 연결은 윤호 담당.
function NotificationsPage() {
  return (
    <div className="mx-auto max-w-[560px] px-4 py-8">
      <EmptyState message="알림 센터 화면 구현 예정" />
    </div>
  );
}

export default NotificationsPage;
