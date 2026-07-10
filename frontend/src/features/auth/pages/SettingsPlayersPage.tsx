import EmptyState from '../../../shared/components/EmptyState';

// 관심 선수 관리 페이지 골격 (USER_FLOW §4.11 — 실제 구현은 팀/선수 페이지 분리).
// 이름 검색으로 등록·삭제. 구현은 local-docs/CODEX_FRONTEND_PROMPT.md 참고.
function SettingsPlayersPage() {
  return (
    <div className="mx-auto max-w-[560px] px-4 py-8">
      <EmptyState message="관심 선수 관리 화면 구현 예정" />
    </div>
  );
}

export default SettingsPlayersPage;
