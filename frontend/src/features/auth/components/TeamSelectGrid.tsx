import { teamFixtures } from '../fixtures';

interface TeamSelectGridProps {
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  dark?: boolean;
}

const MAX_TEAMS = 3;

function TeamSelectGrid({ selectedIds, onChange, dark = false }: TeamSelectGridProps) {
  const toggleTeam = (teamId: string) => {
    if (selectedIds.includes(teamId)) return onChange(selectedIds.filter((id) => id !== teamId));
    if (selectedIds.length < MAX_TEAMS) onChange([...selectedIds, teamId]);
  };

  return (
    <div>
      <p className={`mb-4 text-sm font-semibold ${dark ? 'text-white/60' : 'text-mlb-red'}`}>{selectedIds.length} / {MAX_TEAMS} 선택됨</p>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
        {teamFixtures.map((team) => {
          const selected = selectedIds.includes(team.id);
          const disabled = !selected && selectedIds.length >= MAX_TEAMS;
          return (
            <button
              key={team.id} type="button" aria-pressed={selected} disabled={disabled}
              onClick={() => toggleTeam(team.id)}
              className={`relative flex min-h-28 flex-col items-center justify-center gap-3 rounded-panel border p-3 transition-colors disabled:cursor-not-allowed disabled:opacity-35 ${selected ? 'border-mlb-red bg-red-tint-soft' : dark ? 'border-white/10 bg-white/5 hover:border-white/30' : 'border-card-border bg-white hover:border-mlb-navy'}`}
            >
              {selected && <span className="absolute right-2.5 top-2.5 flex h-5 w-5 items-center justify-center rounded-full bg-mlb-red text-xs font-bold text-white">✓</span>}
              <span className={`flex h-11 w-11 items-center justify-center rounded-full font-display text-sm font-bold ${selected ? 'bg-mlb-navy text-white' : dark ? 'bg-white/10 text-white' : 'bg-page text-text-muted'}`}>{team.abbr}</span>
              <span className={`text-center text-sm font-semibold ${dark ? 'text-white' : 'text-text-strong'}`}>{team.name}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export default TeamSelectGrid;
