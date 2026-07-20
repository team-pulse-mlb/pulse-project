import { useEffect, useState } from 'react';
import { useLocation } from 'react-router';

import { getMe, refreshAccessToken } from '../../features/auth/api/authApi';
import LoginModal from '../../features/auth/components/LoginModal';
import NotificationBell from '../../features/notification/components/NotificationBell';
import Header, {
  IconLink,
  UserIcon,
} from '../../shared/components/Header';

/**
 * 공통 헤더에 로그인 상태별 기능을 조립합니다.
 *
 * 비로그인:
 * - 로그인 버튼
 *
 * 로그인:
 * - 알림 종 아이콘과 드롭다운
 * - 마이페이지 아이콘
 */
function AppHeader() {
  const location = useLocation();

  /**
   * 현재 로그인 여부입니다.
   *
   * 처음에는 localStorage의 Access Token 존재 여부로 판단하고,
   * 아래 restoreLogin 함수에서 서버 확인 및 Refresh Token 재발급을
   * 추가로 시도합니다.
   */
  const [isLoggedIn, setIsLoggedIn] = useState(
    Boolean(localStorage.getItem('accessToken')),
  );

  /**
   * 로그인 모달 열림 여부입니다.
   *
   * setIsLoginModalOpen은 이 useState에서 만들어집니다.
   * 이 선언이 없어지면 로그인 버튼과 LoginModal 부분에서
   * 빨간 줄이 발생합니다.
   */
  const [isLoginModalOpen, setIsLoginModalOpen] =
    useState(false);

  useEffect(() => {
    /**
     * 로그인·로그아웃 과정에서 auth-changed 이벤트가 발생하면
     * localStorage를 다시 확인해 헤더 상태를 갱신합니다.
     */
    const updateLoginState = () => {
      setIsLoggedIn(
        Boolean(localStorage.getItem('accessToken')),
      );
    };

    /**
     * 브라우저 새로고침 후 로그인 상태를 복구합니다.
     *
     * 1. Access Token이 있으면 /me 확인
     * 2. 실패하거나 Access Token이 없으면 Refresh 요청
     * 3. Refresh도 실패하면 로그아웃 상태로 변경
     */
    const restoreLogin = async () => {
      const accessToken =
        localStorage.getItem('accessToken');

      if (accessToken) {
        try {
          await getMe();
          setIsLoggedIn(true);
          return;
        } catch {
          /*
           * Access Token이 만료됐을 수 있으므로
           * 아래에서 Refresh Token 재발급을 시도합니다.
           */
        }
      }

      try {
        const refreshResponse =
          await refreshAccessToken();

        localStorage.setItem(
          'accessToken',
          refreshResponse.accessToken,
        );

        setIsLoggedIn(true);
      } catch {
        localStorage.removeItem('accessToken');
        setIsLoggedIn(false);
      }
    };

    void restoreLogin();

    window.addEventListener(
      'auth-changed',
      updateLoginState,
    );

    window.addEventListener(
      'storage',
      updateLoginState,
    );

    return () => {
      window.removeEventListener(
        'auth-changed',
        updateLoginState,
      );

      window.removeEventListener(
        'storage',
        updateLoginState,
      );
    };
  }, []);

  return (
    <>
      <Header>
        {isLoggedIn ? (
          <>
            {/*
             * 알림 조회·미읽음 점·드롭다운은
             * NotificationBell 내부에서 처리합니다.
             */}
            <NotificationBell />

            <IconLink
              to="/mypage"
              label="마이페이지"
              active={location.pathname.startsWith(
                '/mypage',
              )}
            >
              <UserIcon />
            </IconLink>
          </>
        ) : (
          <button
            type="button"
            onClick={() =>
              setIsLoginModalOpen(true)
            }
            className="rounded-[9px] px-3.5 py-2 text-sm font-semibold text-white/85 transition-colors hover:bg-white/10 hover:text-white"
          >
            로그인
          </button>
        )}
      </Header>

      {/*
       * 로그인 버튼을 누르면 isLoginModalOpen이 true가 되어
       * 로그인 모달이 열립니다.
       */}
      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() =>
          setIsLoginModalOpen(false)
        }
        onLoginSuccess={() => {
          /*
           * 로그인 성공 후 헤더를 즉시 로그인 상태로 바꾸고
           * 로그인 모달을 닫습니다.
           */
          setIsLoggedIn(true);
          setIsLoginModalOpen(false);
        }}
      />
    </>
  );
}

export default AppHeader;
