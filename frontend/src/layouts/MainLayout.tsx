import { Outlet } from 'react-router';
import Header from '../components/Header';

function MainLayout() {
  return (
    <>
      <Header />

      <main>
        <Outlet />
      </main>
    </>
  );
}

export default MainLayout;