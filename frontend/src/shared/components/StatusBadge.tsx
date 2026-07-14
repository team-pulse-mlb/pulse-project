export type GameStatus = 'live' | 'scheduled' | 'final' | 'postponed' | 'canceled';

// 상태별 배지 색은 여기서만 정의한다 (화면마다 중복 정의 금지).
const badgeStyles: Record<GameStatus, string> = {
  live: 'bg-mlb-red text-white',
  scheduled: 'bg-badge-scheduled text-mlb-navy',
  final: 'bg-badge-final text-text-muted',
  postponed: 'bg-badge-final text-text-muted',
  canceled: 'bg-badge-final text-text-muted',
};

const badgeLabels: Record<GameStatus, string> = {
  live: 'LIVE',
  scheduled: '경기 예정',
  final: '종료 경기',
  postponed: '경기 연기',
  canceled: '경기 취소',
};

interface StatusBadgeProps {
  status: GameStatus;

  /** 기본 문구 대신 사용할 상태 문구 */
  label?: string;
}


function StatusBadge({
                         status,
                         label,
                     }: StatusBadgeProps) {
    const shouldShowDot = status === 'live';

    return (
        <span
            className={`inline-flex h-6 items-center gap-1.5 rounded-full px-3 text-[11px] font-extrabold tracking-[0.02em] ${badgeStyles[status]}`}
        >
      {shouldShowDot && (
          <span
              aria-hidden="true"
              className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-current"
          />
      )}

            {label ?? badgeLabels[status]}
    </span>
    );
}

export default StatusBadge;
