import EmptyState from '../../../shared/components/EmptyState';

// 관심 팀 관리 페이지 골격 (USER_FLOW §4.11 — 실제 구현은 팀/선수 페이지 분리).
// 온보딩과 동일한 팀 선택 UI(최대 3팀). 구현은 local-docs/CODEX_FRONTEND_PROMPT.md 참고.
function SettingsTeamsPage() {
  return (
    <div className="mx-auto max-w-[560px] px-4 py-8">
      <EmptyState message="관심 팀 관리 화면 구현 예정" />
    </div>
  );
}

export default SettingsTeamsPage;
