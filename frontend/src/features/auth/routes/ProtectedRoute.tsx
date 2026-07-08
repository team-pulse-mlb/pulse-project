import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';

import { getMe } from '../api/authApi';

type AuthStatus = 'checking' | 'authenticated' | 'unauthenticated';

function ProtectedRoute() {
    const location = useLocation();

    const [authStatus, setAuthStatus] =
        useState<AuthStatus>('checking');

    useEffect(() => {
        const checkAuth = async () => {
        try {
            await getMe();

            setAuthStatus('authenticated');
        } catch (error) {
            console.log('보호 페이지 접근 실패:', error);

            localStorage.removeItem('accessToken');
            window.dispatchEvent(new Event('auth-changed'));

            setAuthStatus('unauthenticated');
        }
        };

        checkAuth();
    }, []);

    if (authStatus === 'checking') {
        return <p>로그인 상태를 확인하는 중입니다...</p>;
    }

    if (authStatus === 'unauthenticated') {
        return (
        <Navigate
            to="/login"
            replace
            state={{ from: location.pathname }}
        />
        );
    }

    return <Outlet />;
}

export default ProtectedRoute;