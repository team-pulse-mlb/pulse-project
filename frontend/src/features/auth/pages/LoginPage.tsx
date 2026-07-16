import {
    useLocation,
    useNavigate,
} from 'react-router';

import LoginModal from '../components/LoginModal';

/*
 * ProtectedRoute가 로그인 페이지로 보낼 때 전달하는 상태입니다.
 *
 * from:
 * - 로그인이 끝난 뒤 다시 돌아갈 보호 페이지 경로
 * - 예: /mypage, /onboarding
 */
interface LoginLocationState {
    from?: string;
}

function LoginPage() {
    const location = useLocation();
    const navigate = useNavigate();

    const loginLocationState =
        location.state as LoginLocationState | null;

    /*
     * LoginModal은 로그인 성공 후에도 onClose를 호출합니다.
     *
     * Access Token이 존재하면 로그인이 성공한 상태이므로
     * 사용자가 원래 접근하려던 보호 페이지로 이동합니다.
     *
     * 토큰이 없으면 사용자가 모달을 닫은 것이므로
     * 홈으로 이동합니다.
     */
    const handleClose = () => {
        const isLoggedIn =
            Boolean(localStorage.getItem('accessToken'));

        const nextPath =
            isLoggedIn
                ? loginLocationState?.from ?? '/'
                : '/';

        navigate(nextPath, {
            replace: true,
        });
    };

    return (
        <LoginModal
            isOpen={true}
            onClose={handleClose}
            onLoginSuccess={() => undefined}
        />
    );
}

export default LoginPage;