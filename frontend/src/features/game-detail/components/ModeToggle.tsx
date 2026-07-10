import SegmentToggle from '../../../shared/components/SegmentToggle';

import type { DisplayMode } from '../lib/displayMode';

interface ModeToggleProps {
  mode: DisplayMode;
  onChange: (mode: DisplayMode) => void;
}

// [보호 | 공개] 세그먼트 토글.
// 두 모드가 항상 함께 보이고 현재 모드가 하이라이트된다.
// 확인창 없이 즉시 전환한다 (USER_FLOW §3.7). 예정 경기 상세에는 노출하지 않는다.
function ModeToggle({ mode, onChange }: ModeToggleProps) {
  return (
    <SegmentToggle<DisplayMode>
      options={[
        { value: 'PROTECTED', label: '보호' },
        { value: 'REVEALED', label: '공개' },
      ]}
      value={mode}
      onChange={onChange}
      ariaLabel="표시 모드"
    />
  );
}

export default ModeToggle;
