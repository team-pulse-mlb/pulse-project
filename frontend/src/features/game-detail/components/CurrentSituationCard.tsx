import type { ReactNode } from 'react';

import Card from '../../../shared/components/Card';

// 진행 중 상세의 "현재 상황" 영역 (USER_FLOW §4.3).
// 보호·공개 모드 공통으로 표시되는 보호 안전 정보만 담는다.
export interface Situation {
  balls: number;
  strikes: number;
  outs: number;
  runnerOnFirst: boolean;
  runnerOnSecond: boolean;
  runnerOnThird: boolean;
  scoringPosition: boolean;
  basesLoaded: boolean;
}

function CountDots({
  label,
  count,
  max,
  colorClass,
}: {
  label: string;
  count: number;
  max: number;
  colorClass: string;
}) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="w-4 font-display text-sm font-bold text-text-muted">
        {label}
      </span>
      <div className="flex gap-1.5">
        {Array.from({ length: max }).map((_, i) => (
          <span
            key={i}
            className={`h-3 w-3 rounded-full ${
              i < count ? colorClass : 'bg-[#E3E7EE]'
            }`}
          />
        ))}
      </div>
    </div>
  );
}

// 주자 다이아몬드: 홈을 아래에 두고, 주자가 있는 베이스는 채운 마름모.
function BaseDiamond({ situation }: { situation: Situation }) {
  const base = (occupied: boolean) =>
    `h-6 w-6 rotate-45 rounded-[4px] border-2 ${
      occupied ? 'border-mlb-red bg-mlb-red' : 'border-[#C9D0DB] bg-white'
    }`;

  return (
    <div
      className="relative h-24 w-24"
      role="img"
      aria-label={`주자 상황: 1루 ${situation.runnerOnFirst ? '있음' : '없음'}, 2루 ${
        situation.runnerOnSecond ? '있음' : '없음'
      }, 3루 ${situation.runnerOnThird ? '있음' : '없음'}`}
    >
      {/* 2루 (위) */}
      <span className={`absolute left-1/2 top-0 -translate-x-1/2 ${base(situation.runnerOnSecond)}`} />
      {/* 3루 (왼쪽) */}
      <span className={`absolute left-0 top-1/2 -translate-y-1/2 ${base(situation.runnerOnThird)}`} />
      {/* 1루 (오른쪽) */}
      <span className={`absolute right-0 top-1/2 -translate-y-1/2 ${base(situation.runnerOnFirst)}`} />
      {/* 홈 (아래) — 주자 개념이 없으므로 항상 빈 표시 */}
      <span className="absolute bottom-0 left-1/2 h-6 w-6 -translate-x-1/2 rotate-45 rounded-[4px] border-2 border-[#C9D0DB] bg-[#EEF1F6]" />
    </div>
  );
}

interface CurrentSituationCardProps {
  /** 이닝 교대 중이거나 타석이 없는 시점이면 null */
  situation: Situation | null;
  /** 공개 모드에서 현재 타석 등 추가로 노출할 내용 */
  children?: ReactNode;
}

function CurrentSituationCard({ situation, children }: CurrentSituationCardProps) {
  return (
    <Card>
      <h3 className="mb-4 text-[15px] font-bold text-text-strong">현재 상황</h3>

      {situation === null ? (
        <p className="py-3 text-sm text-text-muted">이닝 교대 중</p>
      ) : (
        <div className="flex flex-wrap items-center justify-between gap-6">
          <div className="flex flex-col gap-2.5">
            <CountDots label="B" count={situation.balls} max={3} colorClass="bg-dot-ball" />
            <CountDots label="S" count={situation.strikes} max={2} colorClass="bg-dot-strike" />
            <CountDots label="O" count={situation.outs} max={2} colorClass="bg-dot-out" />
          </div>

          <BaseDiamond situation={situation} />

          <div className="flex flex-col items-end gap-1.5">
            {situation.basesLoaded ? (
              <span className="rounded-full bg-red-tint px-3 py-1 text-[13px] font-bold text-mlb-red">
                만루
              </span>
            ) : situation.scoringPosition ? (
              <span className="rounded-full bg-red-tint px-3 py-1 text-[13px] font-bold text-mlb-red">
                득점권
              </span>
            ) : (
              <span className="text-[13px] text-text-faint">주자 상황 표시</span>
            )}
          </div>
        </div>
      )}

      {situation !== null && children && (
        <div className="mt-5 border-t border-divider pt-4">{children}</div>
      )}
    </Card>
  );
}

export default CurrentSituationCard;
