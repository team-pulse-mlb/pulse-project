export type GameStatus = 'live' | 'scheduled' | 'final';

// 상태별 배지 색은 여기서만 정의한다 (화면마다 중복 정의 금지).
const badgeStyles: Record<GameStatus, string> = {
  live: 'bg-mlb-red text-white',
  scheduled: 'bg-badge-scheduled text-mlb-navy',
  final: 'bg-badge-final text-text-muted',
};

const badgeLabels: Record<GameStatus, string> = {
  live: 'LIVE',
  scheduled: '예정',
  final: '종료',
};

interface StatusBadgeProps {
  status: GameStatus;
  /** 배지 안에 표시할 문구. 생략하면 상태 기본 라벨. */
  label?: string;
}

function StatusBadge({ status, label }: StatusBadgeProps) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold tracking-[0.06em] ${badgeStyles[status]}`}
    >
      {status === 'live' && (
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-current" />
      )}
      {label ?? badgeLabels[status]}
    </span>
  );
}

export default StatusBadge;
