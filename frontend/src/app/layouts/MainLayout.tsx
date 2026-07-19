import { Outlet, useLocation } from 'react-router';

import { ToastHost } from '../../shared/components/toast';
import AppHeader from './AppHeader';

// 헤더 없이 전체 화면을 쓰는 경로 (다크 배경 온보딩·회원가입)
const hideHeaderPaths = ['/signup', '/onboarding'];

function MainLayout() {
  const location = useLocation();

  const shouldHideHeader = hideHeaderPaths.includes(location.pathname);

  return (
    <>
      {!shouldHideHeader && <AppHeader />}

      <main>
        <Outlet />
      </main>

      <ToastHost />
    </>
  );
}

export default MainLayout;
