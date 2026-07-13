import { useEffect, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation } from 'react-router';

import { getMe, refreshAccessToken } from '../../features/auth/api/authApi';
import LoginModal from '../../features/auth/components/LoginModal';
import { fetchMyNotifications } from '../api/notificationApi';
import { queryKeys } from '../lib/queryKeys';
import Logo from './Logo';

function BellIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
      <path
        d="M6 9a6 6 0 1 1 12 0c0 4 1.5 5.5 1.5 5.5h-15S6 13 6 9Z"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <path
        d="M10 18.5a2.2 2.2 0 0 0 4 0"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

function UserIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
      <circle cx="12" cy="8.5" r="3.5" stroke="currentColor" strokeWidth="1.8" />
      <path
        d="M4.5 19.5c1.2-3 4-4.5 7.5-4.5s6.3 1.5 7.5 4.5"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}

interface IconLinkProps {
  to: string;
  label: string;
  active: boolean;
  children: ReactNode;
  /** 미읽음 존재 표시 도트 (개수는 스포일러 정책상 표시하지 않음) */
  showDot?: boolean;
}

function IconLink({ to, label, active, children, showDot = false }: IconLinkProps) {
  return (
    <Link
      to={to}
      aria-label={label}
      className={`relative flex h-9 w-9 items-center justify-center rounded-[9px] text-white/85 transition-colors hover:bg-white/10 hover:text-white ${
        active ? 'bg-white/10 text-white' : ''
      }`}
    >
      {children}
      {showDot && (
        <span className="absolute right-1.5 top-1.5 h-1.5 w-1.5 rounded-full bg-mlb-red" />
      )}
    </Link>
  );
}

// 공통 헤더: 다크 고정 바 + 로고(홈 이동) + 우측 로그인 상태 분기.
// 비로그인 = 로그인 버튼, 로그인 = 알림·마이페이지 아이콘 (로그아웃은 마이페이지에 있다).
function Header() {
  const location = useLocation();

  const [isLoggedIn, setIsLoggedIn] = useState(
    Boolean(localStorage.getItem('accessToken')),
  );

  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  const notificationsQuery = useQuery({
    queryKey: queryKeys.me.notifications,
    queryFn: fetchMyNotifications,
    enabled: isLoggedIn,
    retry: false,
    refetchInterval: 60_000,
  });

  const hasUnread = notificationsQuery.data?.some((notification) => !notification.read) ?? false;

  useEffect(() => {
    const updateLoginState = () => {
      setIsLoggedIn(Boolean(localStorage.getItem('accessToken')));
    };

    const restoreLogin = async () => {
      const accessToken = localStorage.getItem('accessToken');

      if (accessToken) {
        try {
          await getMe();
          setIsLoggedIn(true);
          return;
        } catch {
          // Access Token 만료 — 아래에서 재발급을 시도한다.
        }
      }

      try {
        const refreshResponse = await refreshAccessToken();

        localStorage.setItem('accessToken', refreshResponse.accessToken);

        setIsLoggedIn(true);
      } catch {
        localStorage.removeItem('accessToken');
        setIsLoggedIn(false);
      }
    };

    restoreLogin();

    window.addEventListener('auth-changed', updateLoginState);
    window.addEventListener('storage', updateLoginState);

    return () => {
      window.removeEventListener('auth-changed', updateLoginState);
      window.removeEventListener('storage', updateLoginState);
    };
  }, []);

  return (
    <>
      <header className="sticky top-0 z-40 bg-ink">
        <div className="mx-auto flex h-[60px] max-w-[1160px] items-center justify-between px-4">
          <Link to="/" aria-label="PULSE 홈">
            <Logo />
          </Link>

          <nav className="flex items-center gap-1.5">
            {isLoggedIn ? (
              <>
                <IconLink
                  to="/notifications"
                  label="알림 센터"
                  active={location.pathname.startsWith('/notifications')}
                  showDot={hasUnread}
                >
                  <BellIcon />
                </IconLink>
                <IconLink
                  to="/mypage"
                  label="마이페이지"
                  active={location.pathname.startsWith('/mypage')}
                >
                  <UserIcon />
                </IconLink>
              </>
            ) : (
              <button
                type="button"
                onClick={() => setIsLoginModalOpen(true)}
                className="rounded-[9px] px-3.5 py-2 text-sm font-semibold text-white/85 transition-colors hover:bg-white/10 hover:text-white"
              >
                로그인
              </button>
            )}
          </nav>
        </div>
      </header>

      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
        onLoginSuccess={() => setIsLoggedIn(true)}
      />
    </>
  );
}

export default Header;
