import { useEffect, useId, useMemo, useRef, useState } from 'react';

import { formatSlateDateLabel, shiftDate } from '../../../shared/lib/format';

interface DateNavigatorProps {
  /** 현재 표시 중인 슬레이트 날짜 (YYYY-MM-DD). 로딩 전이면 undefined */
  slateDate: string | undefined;
  /** 선택 가능한 마지막 날짜 (YYYY-MM-DD). 오늘 이후는 선택할 수 없다. */
  maxDate: string;
  onChange: (date: string) => void;
}

interface CalendarDay {
  date: string;
  day: number;
  isCurrentMonth: boolean;
}

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
const iconButtonStyle =
  'flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-text-muted transition-all hover:bg-white hover:text-mlb-navy hover:shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mlb-navy/30 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:shadow-none';

function parseDate(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatMonthLabel(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월`;
}

function formatDateAriaLabel(date: string) {
  const [year, month, day] = date.split('-').map(Number);
  return `${year}년 ${month}월 ${day}일`;
}

function createCalendarDays(month: Date): CalendarDay[] {
  const firstDay = new Date(month.getFullYear(), month.getMonth(), 1);
  const gridStart = new Date(firstDay);
  gridStart.setDate(1 - firstDay.getDay());

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    return {
      date: formatDate(date),
      day: date.getDate(),
      isCurrentMonth: date.getMonth() === month.getMonth(),
    };
  });
}

function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg viewBox="0 0 20 20" className="h-4 w-4" fill="none" aria-hidden="true">
      <path
        d={direction === 'left' ? 'm12.5 15-5-5 5-5' : 'm7.5 5 5 5-5 5'}
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CalendarIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" aria-hidden="true">
      <rect x="3.75" y="5.75" width="16.5" height="14.5" rx="3" stroke="currentColor" strokeWidth="1.7" />
      <path d="M3.75 10h16.5M8 3.75v4M16 3.75v4" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  );
}

// 날짜 네비게이터: 하루 이동과 임의 날짜 선택을 한 컨트롤에 묶는다.
// 미래 경기는 상단 추천이 담당하므로 오늘(maxDate) 이후 날짜는 비활성화한다.
function DateNavigator({ slateDate, maxDate, onChange }: DateNavigatorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [visibleMonth, setVisibleMonth] = useState(() => parseDate(slateDate ?? maxDate));
  const containerRef = useRef<HTMLDivElement>(null);
  const dialogId = useId();

  const atMax = !slateDate || slateDate >= maxDate;
  const calendarDays = useMemo(() => createCalendarDays(visibleMonth), [visibleMonth]);
  const nextMonth = new Date(visibleMonth.getFullYear(), visibleMonth.getMonth() + 1, 1);
  const canMoveToNextMonth = formatDate(nextMonth) <= maxDate;

  useEffect(() => {
    if (!isOpen) return;

    const closeOnOutsideClick = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setIsOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsOpen(false);
    };

    document.addEventListener('pointerdown', closeOnOutsideClick);
    document.addEventListener('keydown', closeOnEscape);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsideClick);
      document.removeEventListener('keydown', closeOnEscape);
    };
  }, [isOpen]);

  const toggleCalendar = () => {
    if (!slateDate) return;
    if (!isOpen) setVisibleMonth(parseDate(slateDate));
    setIsOpen((current) => !current);
  };

  const selectDate = (date: string) => {
    if (date > maxDate) return;
    onChange(date);
    setIsOpen(false);
  };

  return (
    <div ref={containerRef} className="relative w-fit">
      <div className="flex items-center rounded-full border border-card-border bg-[#E7EAF0] p-1 shadow-card">
        <button
          type="button"
          aria-label="이전 날짜"
          className={iconButtonStyle}
          disabled={!slateDate}
          onClick={() => slateDate && onChange(shiftDate(slateDate, -1))}
        >
          <ChevronIcon direction="left" />
        </button>

        <button
          type="button"
          aria-label="날짜 선택"
          aria-expanded={isOpen}
          aria-controls={dialogId}
          className="flex h-10 min-w-[174px] items-center justify-center gap-2 rounded-full bg-white px-4 text-[14px] font-semibold text-text-strong shadow-card transition-all hover:text-mlb-navy focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mlb-navy/30 disabled:opacity-40"
          disabled={!slateDate}
          onClick={toggleCalendar}
        >
          <CalendarIcon />
          <span>{slateDate ? formatSlateDateLabel(slateDate) : '날짜 불러오는 중'}</span>
          <span className="text-[11px] font-bold tracking-wide text-text-faint">KST</span>
        </button>

        <button
          type="button"
          aria-label="다음 날짜"
          className={iconButtonStyle}
          disabled={atMax}
          onClick={() => {
            if (!slateDate) return;
            const next = shiftDate(slateDate, 1);
            if (next <= maxDate) onChange(next);
          }}
        >
          <ChevronIcon direction="right" />
        </button>
      </div>

      {isOpen && (
        <div
          id={dialogId}
          role="dialog"
          aria-label="날짜 선택 달력"
          className="absolute left-0 top-[calc(100%+10px)] z-30 w-[min(308px,calc(100vw-32px))] rounded-panel border border-card-border bg-white p-4 shadow-modal"
        >
          <div className="mb-3 flex items-center justify-between">
            <button
              type="button"
              aria-label="이전 달"
              className={iconButtonStyle}
              onClick={() => setVisibleMonth((current) => new Date(current.getFullYear(), current.getMonth() - 1, 1))}
            >
              <ChevronIcon direction="left" />
            </button>
            <strong className="font-display text-[15px] tracking-[-0.01em] text-text-strong">
              {formatMonthLabel(visibleMonth)}
            </strong>
            <button
              type="button"
              aria-label="다음 달"
              className={iconButtonStyle}
              disabled={!canMoveToNextMonth}
              onClick={() => setVisibleMonth(nextMonth)}
            >
              <ChevronIcon direction="right" />
            </button>
          </div>

          <div className="grid grid-cols-7" aria-hidden="true">
            {weekdays.map((weekday, index) => (
              <span
                key={weekday}
                className={`pb-2 text-center text-[11px] font-bold ${
                  index === 0 ? 'text-mlb-red' : index === 6 ? 'text-mlb-navy' : 'text-text-faint'
                }`}
              >
                {weekday}
              </span>
            ))}
          </div>

          <div className="grid grid-cols-7 gap-y-1">
            {calendarDays.map((calendarDay) => {
              const isSelected = calendarDay.date === slateDate;
              const isToday = calendarDay.date === maxDate;
              const isDisabled = calendarDay.date > maxDate;

              return (
                <button
                  key={calendarDay.date}
                  type="button"
                  aria-label={formatDateAriaLabel(calendarDay.date)}
                  aria-current={isToday ? 'date' : undefined}
                  aria-pressed={isSelected}
                  disabled={isDisabled}
                  onClick={() => selectDate(calendarDay.date)}
                  className={`relative mx-auto flex h-9 w-9 items-center justify-center rounded-full text-[13px] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mlb-navy/30 ${
                    isSelected
                      ? 'bg-mlb-navy text-white shadow-card'
                      : isDisabled
                        ? 'cursor-not-allowed text-text-faint/35'
                        : calendarDay.isCurrentMonth
                          ? 'text-text-body hover:bg-badge-scheduled hover:text-mlb-navy'
                          : 'text-text-faint hover:bg-divider'
                  }`}
                >
                  {calendarDay.day}
                  {isToday && !isSelected && (
                    <span className="absolute bottom-0.5 h-1 w-1 rounded-full bg-mlb-red" />
                  )}
                </button>
              );
            })}
          </div>

          <div className="mt-3 flex items-center justify-between border-t border-divider pt-3">
            <span className="text-[11px] font-medium text-text-faint">미래 날짜는 선택할 수 없어요</span>
            <button
              type="button"
              className="rounded-full bg-red-tint px-3 py-1.5 text-xs font-bold text-mlb-red transition-colors hover:bg-[#fbdde3] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-mlb-red/25"
              onClick={() => selectDate(maxDate)}
            >
              오늘
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default DateNavigator;
