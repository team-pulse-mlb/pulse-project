import { createBrowserRouter } from "react-router";

import MainLayout from '../layouts/MainLayout';
import HomePage from '../../features/home/pages/HomePage';
import GameDetailPage from '../../features/game-detail/pages/GameDetailPage';
import SignupPage from '../../features/auth/pages/SignupPage';
import LoginPage from "../../features/auth/pages/LoginPage";
import MyPage from '../../features/auth/pages/MyPage';
import SettingsTeamsPage from '../../features/auth/pages/SettingsTeamsPage';
import SettingsPlayersPage from '../../features/auth/pages/SettingsPlayersPage';
import OnboardingPage from '../../features/auth/pages/OnboardingPage';
import NotificationsPage from '../../features/notification/pages/NotificationsPage';

import ProtectedRoute from '../../features/auth/routes/ProtectedRoute';

const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
      {
        path: 'games/:gameId',
        element: <GameDetailPage />,
      },
      {
        path: 'signup',
        element: <SignupPage />,
      },
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            path: 'onboarding',
            element: <OnboardingPage />,
          },
          {
            path: 'mypage',
            element: <MyPage />,
          },
          {
            // 문서(USER_FLOW §4.11)는 한 화면이지만 실제 구현은 팀/선수 별도 페이지로 분리한다.
            path: 'settings/teams',
            element: <SettingsTeamsPage />,
          },
          {
            path: 'settings/players',
            element: <SettingsPlayersPage />,
          },
          {
            path: 'notifications',
            element: <NotificationsPage />,
          },
        ],
      },
    ],
  },
]);

export default router;
