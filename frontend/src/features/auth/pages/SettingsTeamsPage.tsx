import { useState } from 'react';
import { useNavigate } from 'react-router';

import Card from '../../../shared/components/Card';
import TeamSelectGrid from '../components/TeamSelectGrid';
import { initialFavoriteTeamIds } from '../fixtures';

function SettingsTeamsPage() {
  const navigate = useNavigate();
  const [selectedIds, setSelectedIds] = useState(initialFavoriteTeamIds);
  return (
    <div className="mx-auto max-w-[720px] px-4 py-8">
      <button type="button" onClick={() => navigate(-1)} className="mb-5 text-sm font-semibold text-text-muted hover:text-text-strong">← 뒤로</button>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div><h1 className="font-display text-2xl font-bold text-text-strong">관심 팀 관리</h1><p className="mt-2 text-sm text-text-muted">최대 3팀까지 선택할 수 있습니다.</p></div>
        <button type="button" onClick={() => navigate('/mypage')} className="rounded-input bg-mlb-red px-5 py-2.5 text-sm font-bold text-white hover:opacity-90">저장</button>
      </div>
      <Card><TeamSelectGrid selectedIds={selectedIds} onChange={setSelectedIds} /></Card>
    </div>
  );
}

export default SettingsTeamsPage;
