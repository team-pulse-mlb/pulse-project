import { Outlet, useLocation } from 'react-router';

import Header from '../../shared/components/Header';

function MainLayout() {
  const location = useLocation();

  // Header를 숨길 페이지 목록
  const hideHeaderPaths = ['/signup'];

  // 현재 경로가 숨김 목록에 포함되어 있으면 true
  const shouldHideHeader = hideHeaderPaths.includes(location.pathname);

  return (
    <>
      {!shouldHideHeader && <Header />}

      <main>
        <Outlet />
      </main>
    </>
  );
}

export default MainLayout;