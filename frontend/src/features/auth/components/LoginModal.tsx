import { useRef, useState } from 'react';
import type { MouseEvent, SubmitEventHandler } from 'react';

import { useNavigate } from 'react-router';

import { getMe, login } from '../api/authApi';
import '../styles/loginModal.css';

interface LoginModalProps {
    isOpen: boolean;
    onClose: () => void;
    onLoginSuccess: () => void;
}

const SHOW_SOCIAL_LOGIN = false;
// P2 예정: 네이버/카카오/구글 소셜 로그인
// 지금은 화면에 노출하지 않기 위해 false로 둔다.

function LoginModal({ isOpen, onClose, onLoginSuccess }: LoginModalProps) {
    const navigate = useNavigate();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [loginError, setLoginError] = useState('');

    const [isLoading, setIsLoading] = useState(false);

    const isBackdropMouseDown = useRef(false);

    if (!isOpen) {
        return null;
    }

    const validate = () => {
        let valid = true;

        setEmailError('');
        setPasswordError('');
        setLoginError('');

        if (email.trim() === '') {
        setEmailError('이메일을 입력해 주세요.');
        valid = false;
        }

        if (password.trim() === '') {
        setPasswordError('비밀번호를 입력해 주세요.');
        valid = false;
        }

        return valid;
    };

    const handleLogin: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();
        // form submit 기본 동작은 페이지 새로고침이다.
        // React에서는 새로고침되면 state가 날아가기 때문에 막아야 한다.
        // 이 구조 덕분에 비밀번호 입력 후 Enter도 로그인으로 동작한다.

        if (isLoading) {
        return;
        }

        if (!validate()) {
        return;
        }

        try {
        setIsLoading(true);

        const loginResponse = await login({
            email,
            password,
        });

        localStorage.setItem('accessToken', loginResponse.accessToken);

        // Header, ProtectedRoute 등 다른 컴포넌트에게 로그인 상태 변경을 알려준다.
        window.dispatchEvent(new Event('auth-changed'));

        const meResponse = await getMe();

        console.log('현재 로그인 사용자:', meResponse);

        // Header의 isLoggedIn 상태를 즉시 true로 변경하기 위한 콜백
        onLoginSuccess();

        // 로그인 성공 후 모달 닫기
        onClose();
        } catch (error) {
        console.error(error);
        setLoginError('이메일 또는 비밀번호가 올바르지 않습니다.');
        } finally {
        setIsLoading(false);
        }
    };

    const handleGoSignup = () => {
        onClose();
        navigate('/signup');
    };

    const handleBackdropMouseDown = (event: MouseEvent<HTMLDivElement>) => {
    isBackdropMouseDown.current = event.target === event.currentTarget;
    };

    const handleBackdropClick = (event: MouseEvent<HTMLDivElement>) => {
    if (
        isBackdropMouseDown.current &&
        event.target === event.currentTarget
    ) {
        onClose();
    }

    isBackdropMouseDown.current = false;
    };

    return (
        <div 
            className="login-modal-backdrop" 
            onMouseDown={handleBackdropMouseDown}
            onClick={handleBackdropClick}
        >
        <div
            className="login-modal"
            onClick={(event) => event.stopPropagation()}
        >
            {/* 
            바깥 어두운 배경을 클릭하면 모달이 닫히게 했다.
            대신 모달 내부 클릭까지 닫히면 안 되므로 stopPropagation으로 이벤트 전파를 막는다.
            */}

            <button
            type="button"
            className="login-modal-close"
            onClick={onClose}
            aria-label="로그인 모달 닫기"
            >
            ×
            </button>

            <div className="login-modal-header">
            <h2>로그인</h2>
            <p>PULSE의 개인화 기능을 이용하려면 로그인해 주세요.</p>
            </div>

            <form className="login-modal-form" onSubmit={handleLogin}>
            <div className="login-modal-field">
                <label htmlFor="modal-login-email">이메일</label>
                <input
                id="modal-login-email"
                type="email"
                value={email}
                placeholder="이메일을 입력해 주세요."
                onChange={(event) => setEmail(event.target.value)}
                autoComplete="email"
                />

                <p className="login-modal-error">{emailError}</p>
            </div>

            <div className="login-modal-field">
                <label htmlFor="modal-login-password">비밀번호</label>
                <input
                id="modal-login-password"
                type="password"
                value={password}
                placeholder="비밀번호를 입력해 주세요."
                onChange={(event) => setPassword(event.target.value)}
                autoComplete="current-password"
                />

                <p className="login-modal-error">{passwordError}</p>
            </div>

            <p className="login-modal-login-error">{loginError}</p>

            <button
                type="submit"
                className="login-modal-submit"
                disabled={isLoading}
            >
                {isLoading ? '로그인 중...' : '로그인'}
            </button>
            </form>

            <div className="login-modal-links">
            <button type="button" onClick={handleGoSignup}>
                회원가입
            </button>
            
            </div>

            {SHOW_SOCIAL_LOGIN && (
            <div className="social-login-area">
                <button type="button">네이버로 로그인</button>
                <button type="button">카카오로 로그인</button>
                <button type="button">구글로 로그인</button>
            </div>
            )}
        </div>
        </div>
    );
}

export default LoginModal;