import { useEffect, useRef, useState, type ReactNode } from 'react';

interface RevealProps {
  /** true가 되는 순간 빈 공간에서 페이드+확장으로 자연스럽게 나타난다 */
  show: boolean;
  children: ReactNode;
}

// 동적 등장 래퍼.
// 라이브 갱신으로 나중에 생기는 섹션(헤드라인, 현재 타석, 곡선 등)이
// 화면을 크게 흔들지 않고 나타나도록 grid-rows + opacity 트랜지션을 쓴다.
function Reveal({ show, children }: RevealProps) {
  const [mounted, setMounted] = useState(show);
  const hideTimer = useRef<number | null>(null);

  useEffect(() => {
    let showFrame: number | null = null;
    if (show) {
      if (hideTimer.current !== null) {
        window.clearTimeout(hideTimer.current);
        hideTimer.current = null;
      }
      showFrame = window.requestAnimationFrame(() => setMounted(true));
    } else {
      // 트랜지션이 끝난 뒤 DOM에서 제거한다.
      hideTimer.current = window.setTimeout(() => setMounted(false), 300);
    }

    return () => {
      if (hideTimer.current !== null) {
        window.clearTimeout(hideTimer.current);
      }
      if (showFrame !== null) {
        window.cancelAnimationFrame(showFrame);
      }
    };
  }, [show]);

  return (
    <div
      className={`grid transition-all duration-300 ease-out ${
        show ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
      }`}
    >
      <div className="min-h-0 overflow-hidden">{mounted && children}</div>
    </div>
  );
}

export default Reveal;
