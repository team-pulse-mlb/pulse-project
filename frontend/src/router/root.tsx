import { createBrowserRouter } from "react-router";

import MainLayout from '../layouts/MainLayout';
import HomePage from '../pages/HomePage';
import SignupPage from '../pages/SignupPage';

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
    ],
  },
]);

export default router;