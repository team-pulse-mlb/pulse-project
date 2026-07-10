export interface SegmentOption<T extends string> {
  value: T;
  label: string;
}

interface SegmentToggleProps<T extends string> {
  options: SegmentOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** 접근성용 그룹 이름 (예: "표시 모드", "경기 상태 필터") */
  ariaLabel: string;
}

// 세그먼트 토글: 보호/공개 모드 전환과 홈 상태 필터 탭이 공용으로 사용한다.
// 두 모드(옵션)가 항상 함께 보이고 현재 값이 하이라이트된다 — 라벨이 바뀌는 단일 버튼 금지.
function SegmentToggle<T extends string>({
  options,
  value,
  onChange,
  ariaLabel,
}: SegmentToggleProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className="inline-flex items-center gap-0.5 rounded-full bg-[#E7EAF0] p-1"
    >
      {options.map((option) => {
        const isActive = option.value === value;

        return (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(option.value)}
            className={`rounded-full px-3.5 py-1.5 text-[13.5px] font-semibold transition-colors ${
              isActive
                ? 'bg-white text-text-strong shadow-card'
                : 'text-text-muted hover:text-text-body'
            }`}
          >
            {option.label}
          </button>
        );
      })}
    </div>
  );
}

export default SegmentToggle;
