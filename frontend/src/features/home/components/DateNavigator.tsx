import { useRef } from 'react';

import { formatSlateDateLabel, shiftDate } from '../../../shared/lib/format';

interface DateNavigatorProps {
  /** 현재 표시 중인 슬레이트 날짜 (YYYY-MM-DD). 로딩 전이면 undefined */
  slateDate: string | undefined;
  /** 선택 가능한 마지막 날짜 (YYYY-MM-DD). 오늘 이후는 선택할 수 없다. */
  maxDate: string;
  onChange: (date: string) => void;
}

const arrowButtonStyle =
  'flex h-9 w-9 items-center justify-center rounded-[9px] border border-card-border bg-white text-text-muted transition-colors hover:border-mlb-navy hover:text-mlb-navy disabled:opacity-40';

// 날짜 네비게이터: ◀ 날짜 ▶ + 달력 피커. 기본은 오늘 슬레이트(미 동부시간).
// 미래 경기는 상단 추천이 담당하므로 여기서는 오늘(maxDate) 이후로 이동할 수 없다.
function DateNavigator({ slateDate, maxDate, onChange }: DateNavigatorProps) {
  const dateInputRef = useRef<HTMLInputElement>(null);

  // 오늘이면 다음 날짜로 넘어갈 수 없다 (YYYY-MM-DD는 사전순 비교가 곧 날짜 비교).
  const atMax = !slateDate || slateDate >= maxDate;

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label="이전 날짜"
        className={arrowButtonStyle}
        disabled={!slateDate}
        onClick={() => slateDate && onChange(shiftDate(slateDate, -1))}
      >
        ◀
      </button>

      <span className="min-w-[110px] text-center text-[15px] font-semibold text-text-strong">
        {slateDate ? formatSlateDateLabel(slateDate) : '　'}
      </span>

      <button
        type="button"
        aria-label="다음 날짜"
        className={arrowButtonStyle}
        disabled={atMax}
        onClick={() => {
          if (!slateDate) return;
          const next = shiftDate(slateDate, 1);
          if (next <= maxDate) onChange(next);
        }}
      >
        ▶
      </button>

      <span className="relative">
        <button
          type="button"
          aria-label="날짜 선택"
          className={arrowButtonStyle}
          disabled={!slateDate}
          onClick={() => dateInputRef.current?.showPicker()}
        >
          <svg viewBox="0 0 24 24" className="h-4 w-4" fill="none" aria-hidden="true">
            <rect x="4" y="6" width="16" height="14" rx="2" stroke="currentColor" strokeWidth="1.8" />
            <path d="M4 10h16M8 4v4M16 4v4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
          </svg>
        </button>
        <input
          ref={dateInputRef}
          type="date"
          value={slateDate ?? ''}
          max={maxDate}
          onChange={(event) => {
            const next = event.target.value;
            if (next && next <= maxDate) onChange(next);
          }}
          className="absolute inset-0 -z-10 h-0 w-0 opacity-0"
          tabIndex={-1}
          aria-hidden="true"
        />
      </span>
    </div>
  );
}

export default DateNavigator;
