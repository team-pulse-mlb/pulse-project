import type { DisplayMode } from '../lib/displayMode';

interface ModeToggleProps {
  mode: DisplayMode;
  onChange: (mode: DisplayMode) => void;
}

/**
 * 경기 상세 보호·공개 모드 전환 토글이다.
 *
 * 홈 화면 필터에서 사용하는 공통 SegmentToggle과 분리해,
 * 경기 상세에서만 슬라이딩 버튼과 hover 상호작용을 제공한다.
 */
function ModeToggle({
  mode,
  onChange,
}: ModeToggleProps) {
  const isRevealed =
    mode === 'REVEALED';

  return (
    <div
      role="group"
      aria-label="표시 모드"
      className="relative grid grid-cols-2 rounded-full border border-[#D8DEE8] bg-[#E9EDF3] p-1 shadow-inner"
    >
      {/*
       * 선택된 모드 아래에서 움직이는 토글 손잡이다.
       * 보호 모드는 네이비, 공개 모드는 레드로 구분한다.
       */}
      <span
        aria-hidden="true"
        className={`pointer-events-none absolute bottom-1 left-1 top-1 w-[calc(50%-4px)] rounded-full shadow-[0_3px_10px_rgba(15,23,42,0.18)] transition-all duration-200 ease-out ${
          isRevealed
            ? 'translate-x-full bg-mlb-red'
            : 'translate-x-0 bg-mlb-navy'
        }`}
      />

      <button
        type="button"
        aria-pressed={!isRevealed}
        onClick={() => onChange('PROTECTED')}
        className={`relative z-10 min-w-[76px] cursor-pointer rounded-full px-4 py-2 text-[13.5px] font-bold transition-all duration-200 ${
          !isRevealed
            ? 'text-white'
            : 'text-text-muted hover:-translate-y-0.5 hover:bg-white/70 hover:text-mlb-navy hover:shadow-sm'
        }`}
      >
        보호
      </button>

      <button
        type="button"
        aria-pressed={isRevealed}
        onClick={() => onChange('REVEALED')}
        className={`relative z-10 min-w-[76px] cursor-pointer rounded-full px-4 py-2 text-[13.5px] font-bold transition-all duration-200 ${
          isRevealed
            ? 'text-white'
            : 'text-text-muted hover:-translate-y-0.5 hover:bg-white/70 hover:text-mlb-red hover:shadow-sm'
        }`}
      >
        공개
      </button>
    </div>
  );
}

export default ModeToggle;
