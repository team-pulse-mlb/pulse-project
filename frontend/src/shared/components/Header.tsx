import type { ReactNode } from 'react';
import { Link } from 'react-router';

import Logo from './Logo';

/**
 * 마이페이지 아이콘입니다.
 *
 * 알림 종 아이콘은 NotificationBell 컴포넌트로 분리했기 때문에
 * Header에는 사용자 아이콘만 남겨둡니다.
 */
export function UserIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill="none"
      aria-hidden="true"
    >
      <circle
        cx="12"
        cy="8.5"
        r="3.5"
        stroke="currentColor"
        strokeWidth="1.8"
      />

      <path
        d="M4.5 19.5c1.2-3 4-4.5 7.5-4.5s6.3 1.5 7.5 4.5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

/**
 * 헤더에서 사용하는 일반 아이콘 링크의 Props입니다.
 *
 * 알림 빨간 점은 NotificationBell이 직접 처리하므로
 * 기존 showDot 속성은 제거했습니다.
 */
interface IconLinkProps {
  /** 이동할 경로 */
  to: string;

  /** 스크린 리더용 설명 */
  label: string;

  /** 현재 경로 활성 여부 */
  active: boolean;

  /** 아이콘 */
  children: ReactNode;
}

/**
 * 마이페이지처럼 단순히 특정 경로로 이동하는
 * 헤더 아이콘 링크입니다.
 */
export function IconLink({
  to,
  label,
  active,
  children,
}: IconLinkProps) {
  return (
    <Link
      to={to}
      aria-label={label}
      className={`relative flex h-9 w-9 items-center justify-center rounded-[9px] text-white/85 transition-colors hover:bg-white/10 hover:text-white ${
        active ? 'bg-white/10 text-white' : ''
      }`}
    >
      {children}
    </Link>
  );
}

/**
 * 공통 헤더의 표현 셸입니다.
 */
interface HeaderProps {
  children?: ReactNode;
}

function Header({ children }: HeaderProps) {
  return (
    <header className="sticky top-0 z-40 bg-ink">
      <div className="mx-auto flex h-[60px] max-w-[1160px] items-center justify-between px-4">
        {/* 로고 클릭 시 홈으로 이동합니다. */}
        <Link to="/" aria-label="PULSE 홈">
          <Logo />
        </Link>

        <nav className="flex items-center gap-1.5">
          {children}
        </nav>
      </div>
    </header>
  );
}

export default Header;
