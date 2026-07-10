import type { ReactNode } from 'react';

import StatusBadge, { type GameStatus } from './StatusBadge';

export interface HeroTeam {
  abbr: string;
  name: string;
  /** 공개 모드에서만 값이 있다. 보호 모드는 null → 점수 미렌더링 */
  score?: number | null;
}

interface HeroScoreboardProps {
  status: GameStatus;
  badgeLabel?: string;
  awayTeam: HeroTeam;
  homeTeam: HeroTeam;
  /** 배지 옆 보조 문구 (예: "LIVE · 5회", 시작 시각) */
  subText?: string;
  /** 하단 추가 영역 (AI 헤드라인 등) */
  children?: ReactNode;
}

function TeamSide({ team, align }: { team: HeroTeam; align: 'left' | 'right' }) {
  const alignClass = align === 'left' ? 'items-start' : 'items-end';

  return (
    <div className={`flex min-w-0 flex-col gap-1 ${alignClass}`}>
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-white/10 font-display text-[15px] font-bold text-white">
        {team.abbr}
      </span>
      <span className="truncate text-sm text-white/70">{team.name}</span>
      {typeof team.score === 'number' && (
        <span className="font-display text-5xl font-bold tabular-nums text-white">
          {team.score}
        </span>
      )}
    </div>
  );
}

// 경기 상세 상단 히어로 스코어보드 (진행/종료/예정 공용).
// 보호 모드에서는 score를 넘기지 않으면 점수 영역 자체가 렌더링되지 않는다.
function HeroScoreboard({
  status,
  badgeLabel,
  awayTeam,
  homeTeam,
  subText,
  children,
}: HeroScoreboardProps) {
  return (
    <section className="relative overflow-hidden rounded-hero bg-gradient-to-br from-[#0B2559] to-[#04122E] p-7 shadow-hero">
      {/* 프로토타입의 레드 라디얼 글로우 장식 */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-24 right-0 h-64 w-64 rounded-full bg-mlb-red/15 blur-3xl"
      />

      <div className="relative flex items-center gap-2.5">
        <StatusBadge status={status} label={badgeLabel} />
        {subText && <span className="text-sm text-white/70">{subText}</span>}
      </div>

      <div className="relative mt-5 flex items-start justify-between gap-6">
        <TeamSide team={awayTeam} align="left" />
        <span className="mt-3 font-display text-lg font-semibold text-white/40">
          VS
        </span>
        <TeamSide team={homeTeam} align="right" />
      </div>

      {children && <div className="relative mt-5">{children}</div>}
    </section>
  );
}

export default HeroScoreboard;
