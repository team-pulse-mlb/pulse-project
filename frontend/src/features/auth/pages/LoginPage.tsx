import { useState } from 'react';
import { useNavigate } from 'react-router';

import { getMe, login } from '../api/authApi';

function LoginPage() {
    const navigate = useNavigate();

    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');

    const [emailError, setEmailError] = useState('');
    const [passwordError, setPasswordError] = useState('');
    const [loginError, setLoginError] = useState('');

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

    const handleLogin = async () => {
        if (!validate()) {
        return;
        }

        try {
        const loginResponse = await login({
            email,
            password,
        });

        localStorage.setItem(
            'accessToken',
            loginResponse.accessToken,
        );

        window.dispatchEvent(new Event('auth-changed'));

        const meResponse = await getMe();

        console.log('현재 로그인 사용자:', meResponse);

        navigate('/');
        } catch (error) {
        console.error(error);
        setLoginError('이메일 또는 비밀번호가 올바르지 않습니다.');
        }
    };

    return (
        <div style={{ maxWidth: '420px', margin: '80px auto' }}>
        <h2>로그인</h2>

        <div style={{ marginBottom: '16px' }}>
            <label>이메일</label>
            <input
            type="email"
            value={email}
            placeholder="이메일을 입력해 주세요."
            onChange={(event) => setEmail(event.target.value)}
            style={{
                width: '100%',
                padding: '12px',
                boxSizing: 'border-box',
            }}
            />
            {emailError && (
            <p style={{ color: 'red', margin: '6px 0 0' }}>
                {emailError}
            </p>
            )}
        </div>

        <div style={{ marginBottom: '16px' }}>
            <label>비밀번호</label>
            <input
            type="password"
            value={password}
            placeholder="비밀번호를 입력해 주세요."
            onChange={(event) => setPassword(event.target.value)}
            style={{
                width: '100%',
                padding: '12px',
                boxSizing: 'border-box',
            }}
            />
            {passwordError && (
            <p style={{ color: 'red', margin: '6px 0 0' }}>
                {passwordError}
            </p>
            )}
        </div>

        {loginError && (
            <p style={{ color: 'red' }}>
            {loginError}
            </p>
        )}

        <button
            type="button"
            onClick={handleLogin}
            style={{
            width: '100%',
            padding: '12px',
            cursor: 'pointer',
            }}
        >
            로그인
        </button>

        <button
            type="button"
            onClick={() => navigate('/signup')}
            style={{
            width: '100%',
            padding: '12px',
            marginTop: '8px',
            cursor: 'pointer',
            }}
        >
            회원가입
        </button>
        </div>
    );
}

export default LoginPage;