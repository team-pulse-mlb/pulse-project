import { useState } from 'react';
import { useNavigate } from 'react-router';

import Logo from '../../../shared/components/Logo';
import TeamSelectGrid from '../components/TeamSelectGrid';

function OnboardingPage() {
  const navigate = useNavigate();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);

  return (
    <div className="min-h-screen bg-ink px-4 py-12 text-white">
      <div className="mx-auto max-w-[640px]">
        <div className="mb-10 flex justify-center"><Logo dark={false} /></div>
        <p className="font-display text-sm font-bold tracking-widest text-white/50">STEP 2/2 · 관심 팀</p>
        <h1 className="mt-3 font-display text-3xl font-bold">관심 있는 팀을 선택해주세요</h1>
        <p className="mt-3 text-sm leading-relaxed text-white/60">선택한 팀의 경기가 홈 상단에서 우선 추천됩니다. 나중에 설정에서 바꿀 수 있습니다.</p>
        <div className="mt-6"><TeamSelectGrid selectedIds={selectedIds} onChange={setSelectedIds} dark /></div>
        <div className="mt-8 flex justify-end gap-3">
          <button type="button" onClick={() => navigate('/')} className="rounded-input px-5 py-3 text-sm font-semibold text-white/70 hover:text-white">건너뛰기</button>
          <button type="button" onClick={() => navigate('/')} disabled={selectedIds.length === 0} className="rounded-input bg-mlb-red px-6 py-3 text-sm font-bold text-white disabled:bg-white/10 disabled:text-white/35">완료</button>
        </div>
      </div>
    </div>
  );
}

export default OnboardingPage;
