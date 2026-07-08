import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { getMe, logout, refreshAccessToken } from '../../features/auth/api/authApi';
import LoginModal from '../../features/auth/components/LoginModal';


function Header() {
  const navigate = useNavigate();

  const [isLoggedIn, setIsLoggedIn] = useState(
    Boolean(localStorage.getItem('accessToken')),
  );

  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

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
        } catch (error) {
          console.log('Access Token 확인 실패, 재발급 시도:', error);
        }
      }

      try {
        const refreshResponse = await refreshAccessToken();

        localStorage.setItem(
          'accessToken',
          refreshResponse.accessToken,
        );

        setIsLoggedIn(true);
      } catch (error) {
        console.log('로그인 복구 실패:', error);

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

  const handleLoginSuccess = () => {
      setIsLoggedIn(true);
    };

  const handleLogout = async () => {
    try {
      await logout();
    } catch (error) {
      console.error('로그아웃 요청 실패:', error);
    } finally {
      localStorage.removeItem('accessToken');

      window.dispatchEvent(new Event('auth-changed'));

      navigate('/');
    }
  };

  return (
    <>
      <header className="header">
        <div className="header-inner">
          <h1
            className="logo"
            onClick={() => navigate('/')}
            style={{ cursor: 'pointer' }}
          >
            PULSE
          </h1>

          <nav className="navigation">
            <button type="button" onClick={() => navigate('/')}>
              홈
            </button>

            {isLoggedIn ? (
              <>
                <button type="button" onClick={() => navigate('/mypage')}>
                  내 정보
                </button>

                <button type="button" onClick={handleLogout}>
                  로그아웃
                </button>
              </>
            ) : (
              <>
                <button
                  type="button"
                  onClick={() => setIsLoginModalOpen(true)}
                >
                  로그인
                </button>
              </>
            )}
          </nav>
        </div>
      </header>

      <LoginModal
        isOpen={isLoginModalOpen}
        onClose={() => setIsLoginModalOpen(false)}
        onLoginSuccess={handleLoginSuccess}
      />
    </>
  );
}

export default Header;
