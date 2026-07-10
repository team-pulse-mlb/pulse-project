import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';

import Card from '../../../shared/components/Card';
import ToggleSwitch from '../../../shared/components/ToggleSwitch';
import { getMe, logout, type MeResponse } from '../api/authApi';
import { initialFavoritePlayerIds, initialFavoriteTeamIds, playerFixtures, teamFixtures } from '../fixtures';

interface NotificationSettings { favoriteTeamStart: boolean; surge: boolean; switchSuggestion: boolean; }

function Chip({ label, onRemove, accent = false }: { label: string; onRemove: () => void; accent?: boolean }) {
  return (
    <span className={`inline-flex items-center gap-2 rounded-full px-3 py-2 text-sm font-semibold ${accent ? 'bg-red-tint text-mlb-red' : 'bg-page text-mlb-navy'}`}>
      {label}<button type="button" onClick={onRemove} aria-label={`${label} 삭제`} className="text-current/60 hover:text-current">×</button>
    </span>
  );
}

function MyPage() {
  const navigate = useNavigate();
  const [me, setMe] = useState<MeResponse | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [settings, setSettings] = useState<NotificationSettings>({ favoriteTeamStart: true, surge: true, switchSuggestion: true });
  const [teamIds, setTeamIds] = useState(initialFavoriteTeamIds);
  const [playerIds, setPlayerIds] = useState(initialFavoritePlayerIds);

  useEffect(() => {
    let active = true;
    getMe().then((response) => { if (active) setMe(response); }).catch(() => { if (active) setLoadFailed(true); });
    return () => { active = false; };
  }, []);

  const handleLogout = async () => {
    await logout().catch(() => undefined);
    localStorage.removeItem('accessToken');
    window.dispatchEvent(new Event('auth-changed'));
    navigate('/');
  };

  const notificationRows = [
    { key: 'favoriteTeamStart' as const, title: '관심 팀 경기 시작 알림', description: '등록한 관심 팀 경기가 시작되면 알려드려요.' },
    { key: 'surge' as const, title: '급상승 경기 알림', description: '지금 갑자기 볼 만해진 경기를 알려드려요.' },
    { key: 'switchSuggestion' as const, title: '경기 전환 추천', description: '보는 경기보다 더 볼 만한 경기를 제안해요.' },
  ];

  return (
    <div className="mx-auto flex max-w-[560px] flex-col gap-5 px-4 py-8">
      <h1 className="font-display text-2xl font-bold text-text-strong">마이페이지</h1>
      <Card className="flex items-center justify-between gap-5">
        <div className="min-w-0"><p className="text-xs font-semibold text-text-faint">이메일</p><p className="mt-1 truncate font-semibold text-text-strong">{me?.email ?? (loadFailed ? '계정 정보를 불러오지 못했습니다.' : '불러오는 중...')}</p></div>
        <button type="button" onClick={handleLogout} className="shrink-0 rounded-input border border-card-border px-4 py-2 text-sm font-semibold text-mlb-red hover:border-mlb-red">로그아웃</button>
      </Card>
      <Card>
        <h2 className="mb-2 text-sm font-bold text-text-muted">알림 설정</h2>
        <div className="divide-y divide-divider">
          {notificationRows.map((row) => (
            <div key={row.key} className="flex items-center justify-between gap-4 py-4 first:pt-2 last:pb-0">
              <div><p className="font-semibold text-text-strong">{row.title}</p><p className="mt-1 text-sm text-text-faint">{row.description}</p></div>
              <ToggleSwitch checked={settings[row.key]} onChange={(checked) => setSettings((current) => ({ ...current, [row.key]: checked }))} ariaLabel={row.title} />
            </div>
          ))}
        </div>
      </Card>
      <Card>
        <div className="mb-4 flex items-center justify-between gap-4"><h2 className="text-sm font-bold text-text-muted">관심 팀</h2><Link to="/settings/teams" className="rounded-full border border-card-border px-3 py-1.5 text-sm font-semibold text-mlb-navy hover:border-mlb-navy">+ 추가</Link></div>
        <div className="flex flex-wrap gap-2">{teamIds.map((id) => { const team = teamFixtures.find((item) => item.id === id); return team ? <Chip key={id} label={team.abbr} onRemove={() => setTeamIds((current) => current.filter((teamId) => teamId !== id))} /> : null; })}</div>
      </Card>
      <Card>
        <div className="mb-4 flex items-center justify-between gap-4"><h2 className="text-sm font-bold text-text-muted">관심 선수</h2><Link to="/settings/players" className="rounded-full border border-card-border px-3 py-1.5 text-sm font-semibold text-mlb-navy hover:border-mlb-navy">+ 추가</Link></div>
        <div className="flex flex-wrap gap-2">{playerIds.map((id) => { const player = playerFixtures.find((item) => item.id === id); return player ? <Chip key={id} label={player.name} accent onRemove={() => setPlayerIds((current) => current.filter((playerId) => playerId !== id))} /> : null; })}</div>
      </Card>
    </div>
  );
}

export default MyPage;
