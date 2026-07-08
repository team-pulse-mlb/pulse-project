import { createBrowserRouter } from "react-router";

import MainLayout from '../layouts/MainLayout';
import HomePage from '../pages/HomePage';
import SignupPage from '../../features/auth/pages/SignupPage';
import LoginPage from "../../features/auth/pages/LoginPage";
import MyPage from '../../features/auth/pages/MyPage';

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
            path: 'mypage',
            element: <MyPage />,
          },
        ],
      },
    ],
  },
]);

export default router;