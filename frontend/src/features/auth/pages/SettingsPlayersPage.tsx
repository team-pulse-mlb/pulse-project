import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router';

import Card from '../../../shared/components/Card';
import { initialFavoritePlayerIds, playerFixtures } from '../fixtures';

function SettingsPlayersPage() {
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [selectedIds, setSelectedIds] = useState(initialFavoritePlayerIds);
  const results = useMemo(() => playerFixtures.filter((player) => player.name.toLowerCase().includes(query.trim().toLowerCase())), [query]);
  const togglePlayer = (playerId: number) => setSelectedIds((current) => current.includes(playerId) ? current.filter((id) => id !== playerId) : [...current, playerId]);

  return (
    <div className="mx-auto max-w-[640px] px-4 py-8">
      <button type="button" onClick={() => navigate(-1)} className="mb-5 text-sm font-semibold text-text-muted hover:text-text-strong">← 뒤로</button>
      <div className="mb-6 flex items-end justify-between gap-4">
        <div><h1 className="font-display text-2xl font-bold text-text-strong">관심 선수 관리</h1><p className="mt-2 text-sm text-text-muted">이름으로 검색해 관심 선수를 등록합니다.</p></div>
        <button type="button" onClick={() => navigate('/mypage')} className="rounded-input bg-mlb-red px-5 py-2.5 text-sm font-bold text-white hover:opacity-90">저장</button>
      </div>
      <Card>
        <h2 className="text-sm font-bold text-text-muted">등록된 선수</h2>
        <div className="mt-4 flex flex-wrap gap-2">
          {selectedIds.map((id) => { const player = playerFixtures.find((item) => item.id === id); return player ? <span key={id} className="inline-flex items-center gap-2 rounded-full bg-red-tint px-3 py-2 text-sm font-semibold text-mlb-red">{player.name}<button type="button" onClick={() => togglePlayer(id)} aria-label={`${player.name} 삭제`}>×</button></span> : null; })}
        </div>
        <label htmlFor="player-search" className="mt-6 block text-sm font-bold text-text-muted">선수 검색</label>
        <input id="player-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="선수 이름 검색 (예: Ohtani)" className="mt-2 w-full rounded-input border border-card-border bg-page px-4 py-3 text-sm text-text-strong outline-none focus:border-mlb-navy" />
        <ul className="mt-4 divide-y divide-divider rounded-panel border border-card-border">
          {results.map((player) => {
            const selected = selectedIds.includes(player.id);
            return (
              <li key={player.id} className="flex items-center gap-3 px-4 py-3">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-page font-display text-xs font-bold text-mlb-navy">{player.name.split(' ').map((part) => part[0]).join('').slice(0, 2)}</span>
                <div className="min-w-0 flex-1"><p className="truncate text-sm font-semibold text-text-strong">{player.name}</p><p className="text-xs text-text-faint">{player.team}</p></div>
                <button type="button" onClick={() => togglePlayer(player.id)} className={`rounded-full px-4 py-2 text-sm font-semibold ${selected ? 'bg-mlb-red text-white' : 'border border-card-border text-mlb-navy hover:border-mlb-navy'}`}>{selected ? '추가됨' : '+ 추가'}</button>
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}

export default SettingsPlayersPage;
